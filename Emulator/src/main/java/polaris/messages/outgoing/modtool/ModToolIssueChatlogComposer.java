package polaris.messages.outgoing.modtool;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.hotel.modtool.ModToolChatRecordDataContext;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolIssueChatlogType;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolIssueChatlogComposer extends MessageComposer {
    public static SimpleDateFormat format = new SimpleDateFormat("HH:mm");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final ModToolIssue issue;
    private final List<ModToolChatLog> chatlog;
    private final String roomName;
    private ModToolIssueChatlogType type = ModToolIssueChatlogType.CHAT;

    public ModToolIssueChatlogComposer(ModToolIssue issue, List<ModToolChatLog> chatlog, String roomName) {
        this.issue = issue;
        this.chatlog = chatlog;
        this.roomName = roomName;
    }

    public ModToolIssueChatlogComposer(
            ModToolIssue issue, List<ModToolChatLog> chatlog, String roomName, ModToolIssueChatlogType type) {
        this.issue = issue;
        this.chatlog = chatlog;
        this.roomName = roomName;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolIssueChatlogComposer);
        this.response.appendInt(this.issue.id);
        this.response.appendInt(this.issue.senderId);
        this.response.appendInt(this.issue.reportedId);
        this.response.appendInt(this.issue.roomId);

        // An empty log still gets a record, so the evidence window shows its source and doesn't wait forever.
        Collections.sort(this.chatlog);

        this.response.appendByte(this.type.getType()); // Report Type

        if (this.issue.type == ModToolTicketType.IM) {
            this.response.appendShort(0);
        } else if (this.issue.type == ModToolTicketType.DISCUSSION) {
            this.response.appendShort(this.type == ModToolIssueChatlogType.FORUM_COMMENT ? 3 : 2);

            ModToolChatRecordDataContext.GROUP_ID.append(this.response);
            this.response.appendInt(this.issue.groupId);

            ModToolChatRecordDataContext.THREAD_ID.append(this.response);
            this.response.appendInt(this.issue.threadId);

            if (this.type == ModToolIssueChatlogType.FORUM_COMMENT) {
                ModToolChatRecordDataContext.MESSAGE_ID.append(this.response);
                this.response.appendInt(this.issue.commentId);
            }
        } else if (this.issue.type == ModToolTicketType.PHOTO) {
            this.response.appendShort(3);

            ModToolChatRecordDataContext.ROOM_NAME.append(this.response);
            this.response.appendString(this.roomName);

            ModToolChatRecordDataContext.ROOM_ID.append(this.response);
            this.response.appendInt(this.issue.roomId);

            // The photo can be gone after a restart.
            ModToolChatRecordDataContext.PHOTO_ID.append(this.response);
            this.response.appendString(
                    this.issue.photoItem == null ? "" : BuildersClubItemIds.toWire(this.issue.photoItem.getId()) + "");
        } else {
            this.response.appendShort(3); // Context Count

            ModToolChatRecordDataContext.ROOM_NAME.append(this.response);
            this.response.appendString(this.roomName);

            ModToolChatRecordDataContext.ROOM_ID.append(this.response);
            this.response.appendInt(this.issue.roomId);

            ModToolChatRecordDataContext.GROUP_ID.append(this.response);
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.issue.roomId);
            this.response.appendInt(room == null ? 0 : room.getGuildId());
        }

        this.response.appendShort(this.chatlog.size());
        for (ModToolChatLog chatLog : this.chatlog) {
            this.response.appendString(formatTimestamp(chatLog.timestamp));
            this.response.appendInt(chatLog.userId);
            this.response.appendString(chatLog.username);
            this.response.appendString(chatLog.message);
            this.response.appendBoolean(chatLog.highlighted);
        }
        // }

        return this.response;
    }

    static String formatTimestamp(int timestamp) {
        return ModToolChatlogTimestamp.format(timestamp);
    }

    public ModToolIssue getIssue() {
        return issue;
    }

    public List<ModToolChatLog> getChatlog() {
        return chatlog;
    }

    public String getRoomName() {
        return roomName;
    }

    public ModToolIssueChatlogType getType() {
        return type;
    }
}
