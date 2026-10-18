package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RemoveHabboItemComposer extends MessageComposer {
    private final int itemId;

    public RemoveHabboItemComposer(final int itemId) {
        this.itemId = itemId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveHabboItemComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.itemId));
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }
}
