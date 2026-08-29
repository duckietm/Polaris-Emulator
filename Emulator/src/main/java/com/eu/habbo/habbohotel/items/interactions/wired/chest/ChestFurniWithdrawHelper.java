package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
import java.util.List;

public final class ChestFurniWithdrawHelper {
    private ChestFurniWithdrawHelper() {}

    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, List<ChestFurniStoredItem> removedItems) {
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
        chest.persistContents();

        client.sendResponse(new ChestDataComposer(chest, client.getHabbo()));
        ChestFurniPackets.sendDelta(
                client,
                chest.getId(),
                removedItems.stream().map(row -> row.inventoryId).toList(),
                List.of());

        return delivered;
    }
}
