package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class UpdateStackHeightTileHeightComposer extends MessageComposer {
    private final HabboItem item;
    private final int height;

    public UpdateStackHeightTileHeightComposer(HabboItem item, int height) {
        this.item = item;
        this.height = height;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateStackHeightTileHeightComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendInt(this.height);
        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }

    public int getHeight() {
        return height;
    }
}
