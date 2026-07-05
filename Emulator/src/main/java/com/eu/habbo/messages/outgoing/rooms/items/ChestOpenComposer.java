package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Ack that a wired chest open request was accepted (official Syhytarer / 1174 wire shape: chestId).
 * Nitro uses header 9327 because 806/1174 are taken.
 */
public class ChestOpenComposer extends MessageComposer {
    private final int chestId;

    public ChestOpenComposer(int chestId) {
        this.chestId = chestId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestOpenComposer);
        this.response.appendInt(this.chestId);
        return this.response;
    }

    public int getChestId() {
        return chestId;
    }
}
