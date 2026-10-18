package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** FlatAccessDenied (AIR {@code ?_-H1Z?/?_-T1k?}): the room id, then the user name. */
public class RoomAccessDeniedComposer extends MessageComposer {
    private final int roomId;
    private final String habbo;

    /** Kept for plugins; sends room id 0. */
    public RoomAccessDeniedComposer(String habbo) {
        this(0, habbo);
    }

    public RoomAccessDeniedComposer(int roomId, String habbo) {
        this.roomId = roomId;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomAccessDeniedComposer);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.habbo);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getHabbo() {
        return habbo;
    }
}
