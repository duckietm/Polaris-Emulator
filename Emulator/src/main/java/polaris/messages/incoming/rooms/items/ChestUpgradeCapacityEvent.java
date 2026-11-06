package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestCapacity;
import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.ChestUpgradeResultComposer;

/**
 * Buy capacity levels (AIR {@code WiredChestUpgradeConfirmationView}): {@code int chestId, int
 * levels}. Each level costs {@link #COST_CREDITS} credits and {@link #COST_DIAMONDS} diamonds and adds
 * one AIR capacity step ({@link ChestCapacity}). All or nothing.
 *
 * <p>Answers {@code ChestUpgradeResult} with the official codes: 0 done, 2 past the top level,
 * 4 chest gone, 5 not enough credits, 6 not enough diamonds, 7 not the owner, 10 starter chest. The
 * new capacity level reaches the room with the furni.
 */
public class ChestUpgradeCapacityEvent extends MessageHandler {
    public static final int COST_CREDITS = 10;
    public static final int COST_DIAMONDS = 10;
    public static final int DIAMOND_TYPE = 5;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int levels = this.packet.readInt();

        UserItem item = room.getUserItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_CHEST_GONE);
            return;
        }
        if (user.getUserInfo().getId() != chest.getUserId()) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_NOT_THE_OWNER);
            return;
        }
        if (chest.isStarterChest()) {
            this.refuse(itemId, ChestUpgradeResultComposer.RESULT_STARTER_CHEST);
            return;
        }

        ChestStorage c = chest.getContents();
        int kind = chest.chestKind();

        int result;
        synchronized (user) {
            synchronized (c) {
                // Compared as levels left, never as a sum: a sum with a client-sent count overflows.
                if (levels <= 0 || levels > ChestCapacity.maxLevel(kind, false) - c.getCapacityLevel()) {
                    result = ChestUpgradeResultComposer.RESULT_AT_CAPACITY;
                } else if (user.getUserInfo().getCredits() < COST_CREDITS * levels) {
                    result = ChestUpgradeResultComposer.RESULT_NOT_ENOUGH_CREDITS;
                } else if (user.getUserInfo().getCurrencyAmount(DIAMOND_TYPE) < COST_DIAMONDS * levels) {
                    result = ChestUpgradeResultComposer.RESULT_NOT_ENOUGH_DIAMONDS;
                } else if (!c.upgradeCapacity(levels, kind, false)) {
                    result = ChestUpgradeResultComposer.RESULT_AT_CAPACITY;
                } else {
                    user.giveCredits(-COST_CREDITS * levels);
                    user.givePoints(DIAMOND_TYPE, -COST_DIAMONDS * levels);
                    result = ChestUpgradeResultComposer.RESULT_OK;
                }
            }
        }

        if (result != ChestUpgradeResultComposer.RESULT_OK) {
            this.refuse(itemId, result);
            return;
        }

        chest.persistContents();
        chest.publishSettings(room);
        this.client.sendResponse(new ChestUpgradeResultComposer(itemId, ChestUpgradeResultComposer.RESULT_OK));
    }

    private void refuse(int itemId, int resultCode) {
        this.client.sendResponse(new ChestUpgradeResultComposer(itemId, resultCode));
    }
}
