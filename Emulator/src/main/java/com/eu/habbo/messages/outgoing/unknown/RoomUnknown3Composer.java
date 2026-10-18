package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** YouAreSpectator (AIR {@code ?_-bt?/?_-PI?}): the room id the user watches as a spectator. */
public class RoomUnknown3Composer extends MessageComposer {
    private final int roomId;

    /** Kept for plugins; sends room id 0. */
    public RoomUnknown3Composer() {
        this(0);
    }

    public RoomUnknown3Composer(int roomId) {
        this.roomId = roomId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUnknown3Composer);
        this.response.appendInt(this.roomId);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }
}
