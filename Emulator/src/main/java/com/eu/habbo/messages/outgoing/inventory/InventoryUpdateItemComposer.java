package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
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
        this.response.appendInt(BuildersClubItemIds.toWire(this.habboItem.getGiftAdjustedId()));
        this.response.appendString(this.habboItem.getBaseItem().getType().code);
        this.response.appendInt(BuildersClubItemIds.toWire(this.habboItem.getId()));
        this.response.appendInt(this.habboItem.getBaseItem().getSpriteId());

        // Same category and stuff data as InventoryItemsComposer: a paint item carries its legacy string, every other
        // item its own stuff data, so a monster plant seed keeps the "rarity" map entry AIR's rarity plaque reads.
        String name = this.habboItem.getBaseItem().getName();
        if (name.equals("floor")
                || name.equals("landscape")
                || name.equals("song_disk")
                || name.equals("wallpaper")
                || name.equals("poster")) {
            switch (name) {
                case "landscape":
                    this.response.appendInt(4);
                    break;
                case "floor":
                    this.response.appendInt(3);
                    break;
                case "wallpaper":
                    this.response.appendInt(2);
                    break;
                case "poster":
                    this.response.appendInt(6);
                    break;
                case "song_disk":
                    this.response.appendInt(8);
                    break;
            }
            this.response.appendInt(0);
            this.response.appendString(this.habboItem.getExtradata());
        } else {
            if (name.equals("gnome_box")) this.response.appendInt(13);
            else if (this.habboItem instanceof InteractionWiredChestCurrency) this.response.appendInt(25);
            else if (this.habboItem instanceof InteractionWiredChest) this.response.appendInt(24);
            else
                this.response.appendInt(
                        this.habboItem instanceof InteractionGift
                                ? ((((InteractionGift) this.habboItem).getColorId() * 1000)
                                        + ((InteractionGift) this.habboItem).getRibbonId())
                                : 1);

            this.habboItem.serializeExtradata(this.response);
        }
        this.response.appendBoolean(this.habboItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowTrade());
        this.response.appendBoolean(
                !this.habboItem.isLimited() && this.habboItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowMarketplace());
        this.response.appendInt(this.habboItem.getSecondsToExpiration());
        this.response.appendBoolean(this.habboItem.hasRentPeriod());
        this.response.appendInt(-1);

        if (this.habboItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString(""); // slotId
            this.response.appendInt(0);
        }
        this.response.appendInt(100);
        return this.response;
    }

    public HabboItem getHabboItem() {
        return habboItem;
    }
}
