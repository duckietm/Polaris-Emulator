package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FriendNotificationComposer extends MessageComposer {
    public final static int INSTANT_MESSAGE = -1;
    public final static int ROOM_EVENT = 0;
    public final static int ACHIEVEMENT_COMPLETED = 1;
    public final static int QUEST_COMPLETED = 2;
    public final static int IS_PLAYING_GAME = 3;
    public final static int FINISHED_GAME = 4;
    public final static int INVITE_TO_PLAY_GAME = 5;

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
        this.response.init(Outgoing.FriendToolbarNotificationComposer);
        this.response.appendInt(this.type);
        this.response.appendInt(this.userId);
        this.response.appendString(this.data);
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
