package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Taking coins and furni out of a chest, by a player through the chest window or by wired, with
 * everything that follows: the inventory or wallet, the logs, the owner's notifications, the open
 * windows and the furni's look.
 */
public final class ChestWithdrawals {
    private ChestWithdrawals() {}

    /** What a withdrawal handed out. */
    public record Result(int coins, List<ChestFurniStoredItem> furni) {
        public static final Result NOTHING = new Result(0, List.of());

        public boolean isEmpty() {
            return this.coins <= 0 && this.furni.isEmpty();
        }
    }

    /**
     * Furni out of a furni chest into a player's inventory.
     *
     * @param picker chooses the rows while the chest is held
     * @param source {@link ChestTransactionLog#SOURCE_USER} or {@link ChestTransactionLog#SOURCE_WIRED}
     */
    public static Result furni(
            Habbo habbo,
            InteractionWiredChest chest,
            Room room,
            Function<ChestStorage, List<ChestFurniStoredItem>> picker,
            int source) {
        if (habbo == null || chest == null || chest.chestKind() != ChestStorage.KIND_FURNI) return Result.NOTHING;

        int storedBefore = chest.getContents().furniItemCount();
        Map<ChestFurniStoredItem, Integer> moved =
                chest.withdrawFurni(habbo.getHabboInfo().getId(), picker);
        if (moved.isEmpty()) return Result.NOTHING;

        List<ChestFurniStoredItem> rows = new ArrayList<>(moved.keySet());
        deliver(habbo, moved.values());
        record(chest, room, habbo, source, 0, rows);

        chest.notifyViewers(room, rows.stream().map(row -> row.inventoryId).toList(), List.of());
        chest.persistContents(room);
        afterChange(
                chest,
                room,
                habbo,
                source,
                rows.size(),
                storedBefore,
                chest.getContents().furniItemCount());
        return new Result(0, rows);
    }

    /**
     * Coins out of a coin chest into a player's wallet as credits. A negative amount takes them all.
     */
    public static Result coins(Habbo habbo, InteractionWiredChest chest, Room room, int amount, int source) {
        if (habbo == null || chest == null || chest.chestKind() != ChestStorage.KIND_CURRENCY) return Result.NOTHING;

        int storedBefore = chest.getContents().getCoins();
        int taken = chest.takeCoins(amount);
        if (taken <= 0) return Result.NOTHING;

        habbo.giveCredits(taken);
        record(chest, room, habbo, source, taken, List.of());

        chest.notifyViewers(room, List.of(), List.of());
        chest.persistContents(room);
        afterChange(
                chest,
                room,
                habbo,
                source,
                taken,
                storedBefore,
                chest.getContents().getCoins());
        return new Result(taken, List.of());
    }

    /** Tell the player their own withdrawal went through (AIR success type 1). */
    public static void confirm(Habbo habbo) {
        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient()
                    .sendResponse(new WiredTransactionSuccessComposer(WiredTransactionSuccessComposer.TYPE_WITHDRAW));
        }
    }

    /**
     * Put items that left a chest into a player's inventory. They are already theirs in the database,
     * so a player who is not online simply finds them when they log in.
     *
     * @return how many reached the live inventory
     */
    public static int deliver(Habbo habbo, Collection<Integer> itemIds) {
        if (habbo == null || itemIds == null || itemIds.isEmpty() || habbo.getInventory() == null) return 0;

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getItemManager() == null) return 0;

        int delivered = 0;
        int userId = habbo.getHabboInfo().getId();
        for (HabboItem item : load(new ArrayList<>(itemIds))) {
            // Only items that are theirs and not placed; a withdrawal made them so.
            if (item.getUserId() != userId || item.getRoomId() != 0) continue;
            habbo.getInventory().getItemsComponent().addItem(item);
            if (habbo.getClient() != null) habbo.getClient().sendResponse(new AddHabboItemComposer(item));
            delivered++;
        }
        if (delivered > 0 && habbo.getClient() != null) habbo.getClient().sendResponse(new InventoryRefreshComposer());
        return delivered;
    }

    /** {@link #deliver} for callers that hold ids of items that may or may not be the player's. */
    public static int deliverOwned(Habbo habbo, Collection<Integer> itemIds) {
        return deliver(habbo, itemIds);
    }

    private static final int LOAD_BATCH = 500;

    /** Load items in batches, so emptying a big chest is a handful of queries rather than thousands. */
    private static List<HabboItem> load(List<Integer> itemIds) {
        List<HabboItem> items = new ArrayList<>();
        com.eu.habbo.database.Database database = WiredPlatform.database();
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (database == null || database.getDataSource() == null || environment == null) return items;

        for (int from = 0; from < itemIds.size(); from += LOAD_BATCH) {
            List<Integer> batch = itemIds.subList(from, Math.min(from + LOAD_BATCH, itemIds.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            try (java.sql.Connection connection = database.getDataSource().getConnection();
                    java.sql.PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM items WHERE id IN (" + placeholders + ")")) {
                for (int i = 0; i < batch.size(); i++) statement.setInt(i + 1, batch.get(i));
                try (java.sql.ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        HabboItem item = environment.getItemManager().loadHabboItem(set);
                        if (item != null) items.add(item);
                    }
                }
            } catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(ChestWithdrawals.class)
                        .error("Could not load items that left a wired chest", e);
            }
        }
        return items;
    }

    private static void record(
            InteractionWiredChest chest,
            Room room,
            Habbo habbo,
            int source,
            int coins,
            List<ChestFurniStoredItem> rows) {
        int amount = coins > 0 ? coins : rows.size();
        chest.getContents()
                .addLog(new ChestStorage.LogEntry(
                        "withdraw",
                        System.currentTimeMillis(),
                        habbo.getHabboInfo().getUsername(),
                        amount,
                        0));
        ChestTransactionLog.record(
                room == null ? chest.getRoomId() : room.getId(),
                chest.getId(),
                chest.chestKind(),
                ChestTransactionLog.TYPE_WITHDRAW,
                source,
                habbo,
                coins > 0 ? ChestStorage.COINS_TYPE : -1,
                amount,
                0,
                coins > 0 ? null : rows);
    }

    private static void afterChange(
            InteractionWiredChest chest, Room room, Habbo habbo, int source, int amount, int before, int now) {
        if (source == ChestTransactionLog.SOURCE_WIRED) ChestNotifications.wired(chest, room, amount);
        else ChestNotifications.withdrawal(chest, room, habbo, amount);
        ChestNotifications.afterChange(chest, room, before, now);
    }
}
