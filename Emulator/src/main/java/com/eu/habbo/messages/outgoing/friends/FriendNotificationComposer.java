package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FriendNotificationComposer extends MessageComposer {
    public static final int INSTANT_MESSAGE = -1;
    public static final int ROOM_EVENT = 0;
    public static final int ACHIEVEMENT_COMPLETED = 1;
    public static final int QUEST_COMPLETED = 2;
    public static final int IS_PLAYING_GAME = 3;
    public static final int FINISHED_GAME = 4;
    public static final int INVITE_TO_PLAY_GAME = 5;

    private final int userId;
    private final int type;
    private final String data;

    public FriendNotificationComposer(int userId, int type, String data) {
        this.userId = userId;
        this.type = type;
        this.data = data;
    }

    @Override
    protected ServerMessage composeInternal() {
        // AIR FriendNotificationMessageParser: the avatar id as a string, the type code, the message.
        this.response.init(Outgoing.FriendToolbarNotificationComposer);
        this.response.appendString(String.valueOf(this.userId));
        this.response.appendInt(this.type);
        this.response.appendString(this.data == null ? "" : this.data);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public int getType() {
        return type;
    }

    public String getData() {
        return data;
    }
}
