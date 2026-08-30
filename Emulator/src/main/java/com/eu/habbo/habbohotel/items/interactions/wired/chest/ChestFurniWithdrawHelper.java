package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import java.util.List;

public final class ChestFurniWithdrawHelper {
    private ChestFurniWithdrawHelper() {}

    /**
     * Kept for callers compiled against it. Without a room the chest still empties and still saves; it
     * just cannot tell the room its sprite changed, so a coin chest keeps the fill level it was showing
     * until something else touches it.
     */
    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, List<ChestFurniStoredItem> removedItems) {
        return completeWithdraw(client, chest, null, removedItems);
    }

    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, Room room, List<ChestFurniStoredItem> removedItems) {
        if (client == null || chest == null || removedItems == null || removedItems.isEmpty()) {
            return 0;
        }

        Habbo habbo = client.getHabbo();
        if (habbo == null) {
            return 0;
        }

        int delivered = ChestWiredFurniUtil.giveStoredItemsToInventory(habbo, removedItems);
        client.sendResponse(new InventoryRefreshComposer());
        int withdrawn = removedItems.size();
        chest.getContents()
                .addLog(new ChestStorage.LogEntry(
                        "withdraw",
                        System.currentTimeMillis(),
                        habbo.getHabboInfo().getUsername(),
                        withdrawn,
                        0));
        ChestTransactionLog.record(
                chest.getRoomId(),
                chest.getId(),
                ChestStorage.KIND_FURNI,
                ChestTransactionLog.TYPE_WITHDRAW,
                ChestTransactionLog.SOURCE_USER,
                habbo,
                -1,
                withdrawn,
                0,
                removedItems);
        chest.persistContents(room);

        int storedNow = chest.getContents().furniItemCount();
        ChestNotifications.withdrawal(chest, room, habbo, removedItems.size());
        // The items are already out by the time this runs, so what it held before is what is
        // left plus what just left.
        ChestNotifications.afterChange(chest, room, storedNow + removedItems.size(), storedNow);

        client.sendResponse(new ChestDataComposer(chest, client.getHabbo()));
        ChestFurniPackets.sendDelta(
                client,
                chest.getId(),
                removedItems.stream().map(row -> row.inventoryId).toList(),
                List.of());

        return delivered;
    }
}
