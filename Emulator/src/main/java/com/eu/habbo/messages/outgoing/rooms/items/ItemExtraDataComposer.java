package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ItemExtraDataComposer extends MessageComposer {
    private final HabboItem item;

    public ItemExtraDataComposer(HabboItem item) {
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemExtraDataComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.item.serializeExtradata(this.response);
        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }
}
