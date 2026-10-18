package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RemoveWallItemComposer extends MessageComposer {
    private final HabboItem item;

    public RemoveWallItemComposer(HabboItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveWallItemComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.response.appendInt(this.item.getUserId());
        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }
}
