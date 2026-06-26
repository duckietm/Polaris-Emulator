package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class InventoryUpdateItemComposer extends MessageComposer {
    private final HabboItem habboItem;

    public InventoryUpdateItemComposer(HabboItem item) {
        this.habboItem = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryItemUpdateComposer);
        this.response.appendInt(this.habboItem.getGiftAdjustedId());
        this.response.appendString(this.habboItem.getBaseItem().getType().code);
        this.response.appendInt(this.habboItem.getId());
        this.response.appendInt(this.habboItem.getBaseItem().getSpriteId());

        switch (this.habboItem.getBaseItem().getName()) {
            case "landscape" -> this.response.appendInt(4);
            case "floor" -> this.response.appendInt(3);
            case "wallpaper" -> this.response.appendInt(2);
            case "poster" -> this.response.appendInt(6);
        }

        if (this.habboItem.isLimited()) {
            this.response.appendInt(1);
            this.response.appendInt(256);
            this.response.appendString(this.habboItem.getExtradata());
            this.response.appendInt(this.habboItem.getLimitedSells());
            this.response.appendInt(this.habboItem.getLimitedStack());
        } else {
            this.response.appendInt(1);
            this.response.appendInt(0);
            this.response.appendString(this.habboItem.getExtradata());
        }
        this.response.appendBoolean(this.habboItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowTrade());
        this.response.appendBoolean(!this.habboItem.isLimited() && this.habboItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowMarketplace());
        this.response.appendInt(-1);
        this.response.appendBoolean(false);
        this.response.appendInt(-1);

        if (this.habboItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString(""); //slotId
            this.response.appendInt(0);
        }
        this.response.appendInt(100);
        return this.response;
    }

    public HabboItem getHabboItem() {
        return habboItem;
    }
}
