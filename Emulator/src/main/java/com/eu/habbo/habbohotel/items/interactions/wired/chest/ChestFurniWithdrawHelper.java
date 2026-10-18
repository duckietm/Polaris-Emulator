package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.List;

/**
 * Kept for callers compiled against it; in-tree code uses {@link ChestWithdrawals}.
 *
 * <p>The rows handed in were already taken off the chest's in-memory list; they now leave its store
 * too and reach the player's inventory as the items themselves. Rows the store no longer holds are
 * not delivered.
 */
public final class ChestFurniWithdrawHelper {
    private ChestFurniWithdrawHelper() {}

    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, List<ChestFurniStoredItem> removedItems) {
        return completeWithdraw(client, chest, null, removedItems);
    }

    public static int completeWithdraw(
            GameClient client, InteractionWiredChest chest, Room room, List<ChestFurniStoredItem> removedItems) {
        if (client == null || chest == null || removedItems == null || removedItems.isEmpty()) return 0;

        Habbo habbo = client.getHabbo();
        if (habbo == null) return 0;

        ChestWithdrawals.Result result = ChestWithdrawals.furni(
                habbo, chest, room, storage -> List.copyOf(removedItems), ChestTransactionLog.SOURCE_USER);
        if (!result.isEmpty()) ChestWithdrawals.confirm(habbo);
        return result.furni().size();
    }
}
