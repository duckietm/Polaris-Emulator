package polaris.messages.incoming.modtool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolIssueChatlogType;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolIssueChatlogComposer;

public class ModToolRequestIssueChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int ticketId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(ticketId)) {
                return;
            }

            ModToolIssue issue =
                    Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

            if (issue != null) {
                List<ModToolChatLog> chatlog = new ArrayList<>();
                ModToolIssueChatlogType chatlogType = ModToolIssueChatlogType.CHAT;

                if (issue.type == ModToolTicketType.IM) {
                    chatlog = Emulator.getGameEnvironment()
                            .getModToolManager()
                            .getMessengerChatlog(issue.reportedId, issue.senderId);
                    chatlogType = ModToolIssueChatlogType.IM;
                } else if (issue.type == ModToolTicketType.DISCUSSION) {
                    if (issue.commentId == -1) {
                        chatlogType = ModToolIssueChatlogType.FORUM_THREAD;

                        ForumThread thread = ForumThread.getById(issue.threadId);

                        if (thread != null) {
                            chatlog = thread.getComments().stream()
                                    .map(c -> new ModToolChatLog(
                                            c.getCreatedAt(),
                                            c.getUser().getUserInfo().getId(),
                                            c.getUser().getUserInfo().getUsername(),
                                            c.getMessage()))
                                    .collect(Collectors.toList());
                        }
                    } else {
                        chatlogType = ModToolIssueChatlogType.FORUM_COMMENT;

                        ForumThread thread = ForumThread.getById(issue.threadId);

                        if (thread != null) {
                            chatlog = thread.getComments().stream()
                                    .map(c -> new ModToolChatLog(
                                            c.getCreatedAt(),
                                            c.getUser().getUserInfo().getId(),
                                            c.getUser().getUserInfo().getUsername(),
                                            c.getMessage(),
                                            c.getCommentId() == issue.commentId))
                                    .collect(Collectors.toList());
                        }
                    }
                } else if (issue.type == ModToolTicketType.PHOTO) {
                    if (issue.photoItem != null) {
                        chatlogType = ModToolIssueChatlogType.PHOTO;

                        chatlog = Emulator.getGameEnvironment()
                                .getModToolManager()
                                .getRoomChatlog(issue.roomId);
                    }
                } else {
                    chatlogType = ModToolIssueChatlogType.CHAT;

                    if (issue.roomId > 0) {
                        chatlog = Emulator.getGameEnvironment()
                                .getModToolManager()
                                .getRoomChatlog(issue.roomId);
                    } else {
                        chatlog = new ArrayList<>();
                        chatlog.addAll(Emulator.getGameEnvironment()
                                .getModToolManager()
                                .getUserChatlog(issue.reportedId));
                        chatlog.addAll(Emulator.getGameEnvironment()
                                .getModToolManager()
                                .getUserChatlog(issue.senderId));
                    }
                }

                Room room = ModToolRoomLookup.find(Emulator.getGameEnvironment().getRoomManager(), issue.roomId);
                String roomName = "";

                if (room != null) {
                    roomName = room.getName();
                }
                this.client.sendResponse(new ModToolIssueChatlogComposer(issue, chatlog, roomName, chatlogType));
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.chatlog")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
