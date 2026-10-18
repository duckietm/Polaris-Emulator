package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredOpenComposer extends MessageComposer {
    private final HabboItem item;

    public WiredOpenComposer(HabboItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredOpenComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }
}
