package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code YouAreNotSpectator} (official id 3242, parser
 * {@code class_3180}): the room the client must leave spectator mode for.
 */
public class YouAreNotSpectatorComposer extends MessageComposer {

    private final int roomId;

    public YouAreNotSpectatorComposer(int roomId) {
        this.roomId = roomId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouAreNotSpectatorComposer);

        this.response.appendInt(this.roomId);

        return this.response;
    }

    public int getRoomId() {
        return this.roomId;
    }
}
