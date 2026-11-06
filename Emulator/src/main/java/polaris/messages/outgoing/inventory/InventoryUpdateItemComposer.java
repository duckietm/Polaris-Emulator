package polaris.messages.outgoing.inventory;

import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryUpdateItemComposer extends MessageComposer {
    private final UserItem userItem;

    public InventoryUpdateItemComposer(UserItem item) {
        this.userItem = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryItemUpdateComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.userItem.getGiftAdjustedId()));
        this.response.appendString(this.userItem.getBaseItem().getType().code);
        this.response.appendInt(BuildersClubItemIds.toWire(this.userItem.getId()));
        this.response.appendInt(this.userItem.getBaseItem().getSpriteId());

        // Same category and stuff data as InventoryItemsComposer: a paint item carries its legacy string, every other
        // item its own stuff data, so a monster plant seed keeps the "rarity" map entry AIR's rarity plaque reads.
        String name = this.userItem.getBaseItem().getName();
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
            this.response.appendString(this.userItem.getExtradata());
        } else {
            if (name.equals("gnome_box")) this.response.appendInt(13);
            else if (this.userItem instanceof InteractionWiredChestCurrency) this.response.appendInt(25);
            else if (this.userItem instanceof InteractionWiredChest) this.response.appendInt(24);
            else
                this.response.appendInt(
                        this.userItem instanceof InteractionGift
                                ? ((((InteractionGift) this.userItem).getColorId() * 1000)
                                        + ((InteractionGift) this.userItem).getRibbonId())
                                : 1);

            this.userItem.serializeExtradata(this.response);
        }
        this.response.appendBoolean(this.userItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(this.userItem.getBaseItem().allowTrade());
        this.response.appendBoolean(
                !this.userItem.isLimited() && this.userItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(this.userItem.getBaseItem().allowMarketplace());
        this.response.appendInt(this.userItem.getSecondsToExpiration());
        this.response.appendBoolean(this.userItem.hasRentPeriod());
        this.response.appendInt(-1);

        if (this.userItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString(""); // slotId
            this.response.appendInt(0);
        }
        this.response.appendInt(100);
        return this.response;
    }

    public UserItem getUserItem() {
        return userItem;
    }
}
