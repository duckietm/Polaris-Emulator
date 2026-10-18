package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import java.util.List;

/**
 * Put one inventory item into a furni chest. Kept for callers compiled against it; the official flow
 * is the wired trade ({@link WiredTradingManager#openDeposit}).
 *
 * <p>The item itself moves into the chest's store (AIR model); a chest that refuses it leaves it in
 * the inventory.
 */
public final class ChestFurniDepositHelper {
    private ChestFurniDepositHelper() {}

    public static boolean depositInventoryItem(Habbo habbo, InteractionWiredChest chest, HabboItem inventoryItem) {
        if (habbo == null || chest == null || inventoryItem == null) return false;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return false;
        if (ChestTransactionFailures.depositRefusal(chest, habbo, room.hasRights(habbo))
                != ChestTransactionFailures.NONE) {
            return false;
        }
        if (chest.chestKind() != ChestStorage.KIND_FURNI || !InteractionWiredChest.canStore(inventoryItem))
            return false;

        HabboItem held = habbo.getInventory().getItemsComponent().getHabboItem(inventoryItem.getId());
        if (held == null || !habbo.getInventory().getItemsComponent().takeHabboItemsAtomically(List.of(held))) {
            return false;
        }

        int storedBefore = chest.getContents().furniItemCount();
        if (chest.depositFurni(habbo.getHabboInfo().getId(), List.of(held)).isEmpty()) {
            habbo.getInventory().getItemsComponent().addItem(held);
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
                        habbo.getHabboInfo().getUsername(),
                        0,
                        1));
        ChestTransactionLog.record(
                chest.getRoomId(),
                chest.getId(),
                ChestStorage.KIND_FURNI,
                ChestTransactionLog.TYPE_DEPOSIT,
                ChestTransactionLog.SOURCE_USER,
                habbo,
                -1,
                0,
                1,
                rows);
        chest.notifyViewers(room, List.of(), rows);
        chest.persistContents(room);

        ChestNotifications.donation(chest, room, habbo, 1);
        ChestNotifications.afterChange(
                chest, room, storedBefore, chest.getContents().furniItemCount());

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new RemoveHabboItemComposer(held.getGiftAdjustedId()));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
        return true;
    }
}
