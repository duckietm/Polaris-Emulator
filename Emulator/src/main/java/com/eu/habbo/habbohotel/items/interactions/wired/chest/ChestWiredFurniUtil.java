package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ChestWiredFurniUtil {
    private ChestWiredFurniUtil() {}

    public static int giveFromChest(Habbo habbo, InteractionWiredChest chest, int amount) {
        return giveFromChestByType(habbo, chest, false, 0, "", amount);
    }

    /**
     * Move up to {@code amount} stored items of one type from the chest into the player's inventory.
     * The items themselves move (AIR model); nothing is copied.
     */
    public static int giveFromChestByType(
            Habbo habbo,
            InteractionWiredChest chest,
            boolean wallItem,
            int baseItemId,
            String legacyPosterId,
            int amount) {
        if (habbo == null || chest == null || amount <= 0 || baseItemId <= 0) return 0;

        return ChestWithdrawals.furni(
                        habbo,
                        chest,
                        null,
                        storage -> storage.removeFurniByType(wallItem, baseItemId, legacyPosterId, amount),
                        ChestTransactionLog.SOURCE_WIRED)
                .furni()
                .size();
    }

    /**
     * Refused: the AIR chest model never creates furni out of nothing. Kept for callers compiled
     * against it.
     *
     * @return always 0
     */
    public static int mintToInventory(Habbo habbo, boolean wallItem, int baseItemId, int amount) {
        return 0;
    }

    public static int countInInventory(Habbo habbo, boolean wallItem, int baseItemId, String legacyPosterId) {
        if (habbo == null || baseItemId <= 0) {
            return 0;
        }
        int count = 0;
        for (HabboItem item : inventoryItems(habbo)) {
            if (matchesFurniType(item, wallItem, baseItemId, legacyPosterId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Refused: items only go into a chest through {@link InteractionWiredChest#depositFurni}, which
     * moves the items themselves. This used to delete the items and keep a copy that was never saved.
     * Kept for callers compiled against it.
     *
     * @return always empty; nothing leaves the inventory
     */
    public static List<ChestFurniStoredItem> takeFromInventory(
            Habbo habbo, boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        return new ArrayList<>();
    }

    /** Refused for the same reason as {@link #takeFromInventory}; kept for callers compiled against it. */
    public static void depositToChest(InteractionWiredChest chest, List<ChestFurniStoredItem> items) {
        // Intentionally nothing: rows that are not in the chest's store must not appear in it.
    }

    /**
     * Put stored rows that already left a chest (and so are the player's items in the database) into
     * the player's live inventory. Nothing is created: a row that is not an item of theirs is skipped.
     */
    public static int giveStoredItemsToInventory(Habbo habbo, List<ChestFurniStoredItem> removed) {
        if (habbo == null || removed == null || removed.isEmpty()) return 0;

        List<Integer> ids = new ArrayList<>();
        for (ChestFurniStoredItem row : removed) {
            if (row != null && habbo.getInventory().getItemsComponent().getHabboItem(row.inventoryId) == null) {
                ids.add(row.inventoryId);
            }
        }
        return ChestWithdrawals.deliverOwned(habbo, ids);
    }

    private static Collection<HabboItem> inventoryItems(Habbo habbo) {
        return habbo.getInventory().getItemsComponent().getItemsAsValueCollection();
    }

    private static boolean matchesFurniType(HabboItem item, boolean wallItem, int baseItemId, String legacyPosterId) {
        if (item == null) {
            return false;
        }
        Item base = item.getBaseItem();
        if (base == null || base.getId() != baseItemId) {
            return false;
        }
        if ((base.getType() == FurnitureType.WALL) != wallItem) {
            return false;
        }
        if (!wallItem) {
            return true;
        }
        String poster = legacyPosterId == null ? "" : legacyPosterId;
        String extra = item.getExtradata() == null ? "" : item.getExtradata();
        return poster.isEmpty() || poster.equals(extra);
    }
}
