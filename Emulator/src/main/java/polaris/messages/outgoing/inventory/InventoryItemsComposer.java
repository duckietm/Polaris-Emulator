package polaris.messages.outgoing.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryItemsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryItemsComposer.class);

    private final int fragmentNumber;
    private final int totalFragments;
    private final Int2ObjectMap<UserItem> items;

    public InventoryItemsComposer(int fragmentNumber, int totalFragments, Int2ObjectMap<UserItem> items) {
        this.fragmentNumber = fragmentNumber;
        this.totalFragments = totalFragments;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        try {
            this.response.init(Outgoing.InventoryItemsComposer);
            this.response.appendInt(this.totalFragments);
            this.response.appendInt(this.fragmentNumber - 1);
            this.response.appendInt(this.items.size());

            for (UserItem userItem : this.items.values()) {
                this.serializeItem(userItem);
            }
            return this.response;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    private void serializeItem(UserItem userItem) {
        this.response.appendInt(BuildersClubItemIds.toWire(userItem.getGiftAdjustedId()));
        this.response.appendString(userItem.getBaseItem().getType().code);
        this.response.appendInt(BuildersClubItemIds.toWire(userItem.getId()));
        this.response.appendInt(userItem.getBaseItem().getSpriteId());

        if (userItem.getBaseItem().getName().equals("floor")
                || userItem.getBaseItem().getName().equals("landscape")
                || userItem.getBaseItem().getName().equals("song_disk")
                || userItem.getBaseItem().getName().equals("wallpaper")
                || userItem.getBaseItem().getName().equals("poster")) {
            switch (userItem.getBaseItem().getName()) {
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
            this.addExtraDataToResponse(userItem);
        } else {
            if (userItem.getBaseItem().getName().equals("gnome_box")) this.response.appendInt(13);
            // Official `FurniCategory` (AIR 13): a chest reports 24 (furni chest,
            // brown) or 25 (coins chest, gold) so `GroupItem.updateItemImageVisual`
            // draws the chest overlay with its contents count on the inventory tile.
            else if (userItem instanceof InteractionWiredChestCurrency) this.response.appendInt(25);
            else if (userItem instanceof InteractionWiredChest) this.response.appendInt(24);
            else
                this.response.appendInt(
                        userItem instanceof InteractionGift
                                ? ((((InteractionGift) userItem).getColorId() * 1000)
                                        + ((InteractionGift) userItem).getRibbonId())
                                : 1);

            userItem.serializeExtradata(this.response);
        }
        this.response.appendBoolean(userItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(userItem.getBaseItem().allowTrade());
        this.response.appendBoolean(
                !userItem.isLimited() && userItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(userItem.getBaseItem().allowMarketplace());
        this.response.appendInt(userItem.getSecondsToExpiration());
        this.response.appendBoolean(true);
        this.response.appendInt(-1);

        if (userItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString("");
            if (userItem.getBaseItem().getName().equals("song_disk")) {
                List<String> extraDataAsList =
                        Arrays.asList(userItem.getExtradata().split("\n"));
                this.response.appendInt(Integer.valueOf(extraDataAsList.get(extraDataAsList.size() - 1)));
                return;
            }
            this.response.appendInt(
                    userItem instanceof InteractionGift
                            ? ((((InteractionGift) userItem).getColorId() * 1000)
                                    + ((InteractionGift) userItem).getRibbonId())
                            : 1);
        }
    }

    public void addExtraDataToResponse(UserItem userItem) {
        this.response.appendInt(0);
        this.response.appendString(userItem.getExtradata());
    }

    public int getFragmentNumber() {
        return fragmentNumber;
    }

    public int getTotalFragments() {
        return totalFragments;
    }

    public Int2ObjectMap<UserItem> getItems() {
        return items;
    }
}
