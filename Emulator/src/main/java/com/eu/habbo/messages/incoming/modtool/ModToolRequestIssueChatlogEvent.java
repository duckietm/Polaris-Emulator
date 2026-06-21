package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.modtool.*;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueChatlogComposer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModToolRequestIssueChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int ticketId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(ticketId)) {
                return;
            }

            ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

            if (issue != null) {
                List<ModToolChatLog> chatlog = new ArrayList<>();
                ModToolIssueChatlogType chatlogType = ModToolIssueChatlogType.CHAT;

                if (issue.type == ModToolTicketType.IM) {
                    chatlog = Emulator.getGameEnvironment().getModToolManager().getMessengerChatlog(issue.reportedId, issue.senderId);
                    chatlogType = ModToolIssueChatlogType.IM;
                } else if (issue.type == ModToolTicketType.DISCUSSION) {
                    if (issue.commentId == -1) {
                        chatlogType = ModToolIssueChatlogType.FORUM_THREAD;

                        ForumThread thread = ForumThread.getById(issue.threadId);

                        if (thread != null) {
                            chatlog = thread.getComments().stream().map(c -> new ModToolChatLog(c.getCreatedAt(), c.getHabbo().getHabboInfo().getId(), c.getHabbo().getHabboInfo().getUsername(), c.getMessage())).collect(Collectors.toList());
                        }
                    } else {
                        chatlogType = ModToolIssueChatlogType.FORUM_COMMENT;

                        ForumThread thread = ForumThread.getById(issue.threadId);

                        if (thread != null) {
                            chatlog = thread.getComments().stream().map(c -> new ModToolChatLog(c.getCreatedAt(), c.getHabbo().getHabboInfo().getId(), c.getHabbo().getHabboInfo().getUsername(), c.getMessage(), c.getCommentId() == issue.commentId)).collect(Collectors.toList());
                        }
                    }
                } else if (issue.type == ModToolTicketType.PHOTO) {
                    if (issue.photoItem != null) {
                        chatlogType = ModToolIssueChatlogType.PHOTO;

                        chatlog = Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(issue.roomId);
                    }
                } else {
                    chatlogType = ModToolIssueChatlogType.CHAT;

                    if (issue.roomId > 0) {
                        chatlog = Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(issue.roomId);
                    } else {
                        chatlog = new ArrayList<>();
                        chatlog.addAll(Emulator.getGameEnvironment().getModToolManager().getUserChatlog(issue.reportedId));
                        chatlog.addAll(Emulator.getGameEnvironment().getModToolManager().getUserChatlog(issue.senderId));
                    }
                }

                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(issue.roomId);
                String roomName = "";

                if (room != null) {
                    roomName = room.getName();
                }
                this.client.sendResponse(new ModToolIssueChatlogComposer(issue, chatlog, roomName, chatlogType));
            }
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.chatlog").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
