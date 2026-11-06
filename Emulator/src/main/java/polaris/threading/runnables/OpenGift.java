package polaris.threading.runnables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.InventoryUpdateItemComposer;
import polaris.messages.outgoing.rooms.items.PresentItemOpenedComposer;

public class OpenGift implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGift.class);

    private final UserItem item;
    private final User user;
    private final Room room;

    public OpenGift(UserItem item, User user, Room room) {
        this.item = item;
        this.user = user;
        this.room = room;
    }

    /**
     * A furni the gift still holds: on no room, and with no owner yet, or owned by the gift's owner for gifts
     * made before contents were ownerless. One that was given away since is not taken back by opening.
     */
    static boolean stillWrapped(UserItem content, int giftOwnerId) {
        return content.getRoomId() == 0
                && (content.getUserId() == ItemManager.GIFT_CONTENT_OWNER || content.getUserId() == giftOwnerId);
    }

    @Override
    public void run() {
        try {
            // The gift has to still be in this room: removed or picked up since the open was scheduled,
            // delivering its contents would leave the wrapper and the contents both.
            if (this.room.getUserItem(this.item.getId()) != this.item) {
                return;
            }

            UserItem inside = null;

            Collection<UserItem> items = ((InteractionGift) this.item).loadItems();
            // An item that no longer loads is skipped instead of failing the whole gift.
            items.removeIf(Objects::isNull);
            int giftOwnerId = this.item.getUserId();
            items.removeIf(content -> !stillWrapped(content, giftOwnerId));
            for (UserItem i : items) {
                if (inside == null) inside = i;

                i.setUserId(this.user.getUserInfo().getId());
                i.needsUpdate(true);
                i.run();
            }

            if (inside != null) inside.setFromGift(true);

            this.user.getInventory().getItemsComponent().addItems(items);

            RoomTile tile = this.room.getLayout().getTile(this.item.getX(), this.item.getY());
            if (tile != null) {
                this.room.updateTile(tile);
            }

            Emulator.getThreading().runPersistence(new QueryDeleteUserItem(this.item.getId()));
            Emulator.getThreading()
                    .run(
                            new RemoveFloorItemTask(this.room, this.item),
                            this.item.getBaseItem().getName().contains("present_wrap") ? 5000 : 0);

            this.user.getClient().sendResponse(new InventoryRefreshComposer());

            Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = new HashMap<>();

            for (UserItem item : items) {
                switch (item.getBaseItem().getType()) {
                    case WALL:
                    case FLOOR:
                        if (!unseenItems.containsKey(AddUserItemComposer.AddUserItemCategory.OWNED_FURNI))
                            unseenItems.put(AddUserItemComposer.AddUserItemCategory.OWNED_FURNI, new ArrayList<>());

                        unseenItems
                                .get(AddUserItemComposer.AddUserItemCategory.OWNED_FURNI)
                                .add(item.getGiftAdjustedId());

                        break;

                    case BADGE:
                        if (!unseenItems.containsKey(AddUserItemComposer.AddUserItemCategory.BADGE))
                            unseenItems.put(AddUserItemComposer.AddUserItemCategory.BADGE, new ArrayList<>());

                        unseenItems
                                .get(AddUserItemComposer.AddUserItemCategory.BADGE)
                                .add(item.getId()); // badges cannot be placed so no need for gift adjusted ID
                        break;

                    case PET:
                        if (!unseenItems.containsKey(AddUserItemComposer.AddUserItemCategory.PET))
                            unseenItems.put(AddUserItemComposer.AddUserItemCategory.PET, new ArrayList<>());

                        unseenItems
                                .get(AddUserItemComposer.AddUserItemCategory.PET)
                                .add(item.getGiftAdjustedId());
                        break;

                    case ROBOT:
                        if (!unseenItems.containsKey(AddUserItemComposer.AddUserItemCategory.BOT))
                            unseenItems.put(AddUserItemComposer.AddUserItemCategory.BOT, new ArrayList<>());

                        unseenItems
                                .get(AddUserItemComposer.AddUserItemCategory.BOT)
                                .add(item.getGiftAdjustedId());
                        break;

                    default:
                        break;
                }
            }

            this.user.getClient().sendResponse(new AddUserItemComposer(unseenItems));

            if (inside != null) {
                this.user.getClient().sendResponse(new InventoryUpdateItemComposer(inside));
                this.user.getClient().sendResponse(new PresentItemOpenedComposer(inside, "", false));
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
