package polaris.hotel.modtool;

import polaris.messages.ServerMessage;

public enum ModToolChatRecordDataContext {
    ROOM_NAME("roomName", 2),
    ROOM_ID("roomId", 1),
    GROUP_ID("groupId", 1),
    THREAD_ID("threadId", 1),
    MESSAGE_ID("messageId", 1),
    PHOTO_ID("extraDataId", 2);

    public final String key;
    public final int type;

    ModToolChatRecordDataContext(String key, int type) {
        this.key = key;
        this.type = type;
    }

    public void append(final ServerMessage message) {
        message.appendString(this.key);
        message.appendByte(this.type);
    }
}
