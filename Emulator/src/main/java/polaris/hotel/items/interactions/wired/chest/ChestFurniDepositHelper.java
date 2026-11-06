package polaris.hotel.items.interactions.wired.chest;

import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;

/**
 * Put one inventory item into a furni chest. Kept for callers compiled against it; the official flow
 * is the wired trade ({@link WiredTradingManager#openDeposit}).
 *
 * <p>The item itself moves into the chest's store (AIR model); a chest that refuses it leaves it in
 * the inventory.
 */
public final class ChestFurniDepositHelper {
    private ChestFurniDepositHelper() {}

    public static boolean depositInventoryItem(User user, InteractionWiredChest chest, UserItem inventoryItem) {
        if (user == null || chest == null || inventoryItem == null) return false;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return false;
        if (ChestTransactionFailures.depositRefusal(chest, user, room.hasRights(user))
                != ChestTransactionFailures.NONE) {
            return false;
        }
        if (chest.chestKind() != ChestStorage.KIND_FURNI || !InteractionWiredChest.canStore(inventoryItem))
            return false;

        UserItem held = user.getInventory().getItemsComponent().getUserItem(inventoryItem.getId());
        if (held == null || !user.getInventory().getItemsComponent().takeUserItemsAtomically(List.of(held))) {
            return false;
        }

        int storedBefore = chest.getContents().furniItemCount();
        if (chest.depositFurni(user.getUserInfo().getId(), List.of(held)).isEmpty()) {
            user.getInventory().getItemsComponent().addItem(held);
            return false;
        }

        ChestFurniStoredItem row = null;
        for (ChestFurniStoredItem stored : chest.getContents().furniItems()) {
            if (stored.inventoryId == held.getId()) row = stored;
        }
        List<ChestFurniStoredItem> rows = row == null ? List.of() : List.of(row);

        chest.getContents()
                .addLog(new ChestStorage.LogEntry(
                        "deposit",
                        System.currentTimeMillis(),
                        user.getUserInfo().getUsername(),
                        0,
                        1));
        ChestTransactionLog.record(
                chest.getRoomId(),
                chest.getId(),
                ChestStorage.KIND_FURNI,
                ChestTransactionLog.TYPE_DEPOSIT,
                ChestTransactionLog.SOURCE_USER,
                user,
                -1,
                0,
                1,
                rows);
        chest.notifyViewers(room, List.of(), rows);
        chest.persistContents(room);

        ChestNotifications.donation(chest, room, user, 1);
        ChestNotifications.afterChange(
                chest, room, storedBefore, chest.getContents().furniItemCount());

        if (user.getClient() != null) {
            user.getClient().sendResponse(new RemoveUserItemComposer(held.getGiftAdjustedId()));
            user.getClient().sendResponse(new InventoryRefreshComposer());
        }
        return true;
    }
}
