package polaris.hotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.earnings.WiredEarnings;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.items.WiredTransactionSuccessComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;

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
            User user,
            InteractionWiredChest chest,
            Room room,
            Function<ChestStorage, List<ChestFurniStoredItem>> picker,
            int source) {
        if (user == null || chest == null || chest.chestKind() != ChestStorage.KIND_FURNI) return Result.NOTHING;

        int storedBefore = chest.getContents().furniItemCount();
        Map<ChestFurniStoredItem, Integer> moved =
                chest.withdrawFurni(user.getUserInfo().getId(), picker);
        if (moved.isEmpty()) return Result.NOTHING;

        List<ChestFurniStoredItem> rows = new ArrayList<>(moved.keySet());
        deliver(user, moved.values());
        record(chest, room, user, source, 0, rows);

        chest.notifyViewers(room, rows.stream().map(row -> row.inventoryId).toList(), List.of());
        chest.persistContents(room);
        afterChange(
                chest,
                room,
                user,
                source,
                rows.size(),
                storedBefore,
                chest.getContents().furniItemCount());
        return new Result(0, rows);
    }

    /**
     * Coins out of a coin chest into a player's wallet as credits. A negative amount takes them all. Coins wired
     * hands out go to the games row of the earnings window ({@link WiredEarnings}).
     */
    public static Result coins(User user, InteractionWiredChest chest, Room room, int amount, int source) {
        return coins(user, chest, room, amount, source, WiredEarnings.CATEGORY_GAMES);
    }

    /**
     * Coins out of a coin chest. A player's own withdrawal goes into their wallet; coins wired hands out go to the
     * earnings window row of {@code earningsCategory} (AIR 11 games, 13 agency) when the hotel runs it.
     */
    public static Result coins(
            User user, InteractionWiredChest chest, Room room, int amount, int source, int earningsCategory) {
        if (user == null || chest == null || chest.chestKind() != ChestStorage.KIND_CURRENCY) return Result.NOTHING;

        int storedBefore = chest.getContents().getCoins();
        int taken = chest.takeCoins(amount);
        if (taken <= 0) return Result.NOTHING;

        if (source == ChestTransactionLog.SOURCE_WIRED) {
            WiredEarnings.get().pay(user, taken, earningsCategory);
        } else if (!PAYER.get().pay(user, chest.getId(), taken)) {
            // The purse refused (a plugin cancelled it or the ledger failed): the coins go back into the
            // chest rather than vanishing between the two.
            chest.addCoins(taken);
            return Result.NOTHING;
        }
        record(chest, room, user, source, taken, List.of());

        chest.notifyViewers(room, List.of(), List.of());
        chest.persistContents(room);
        afterChange(
                chest,
                room,
                user,
                source,
                taken,
                storedBefore,
                chest.getContents().getCoins());
        return new Result(taken, List.of());
    }

    /** Pays a player's own coin withdrawal into their purse. */
    @FunctionalInterface
    interface CreditPayer {
        /** @return false when the credits did not reach the purse */
        boolean pay(User user, int chestId, int credits);
    }

    /**
     * The purse payment of a withdrawal, through the economy ledger under its own reason and the chest's id, like
     * every wallet change.
     */
    static final CreditPayer LEDGER_PAYER = (user, chestId, credits) -> {
        if (user == null || user.getUserInfo() == null || credits <= 0) return false;
        polaris.plugin.events.users.UserCreditsEvent event =
                new polaris.plugin.events.users.UserCreditsEvent(user, credits);
        polaris.plugin.PluginManager plugins = WiredPlatform.pluginManager();
        if (plugins != null && plugins.fireEvent(event).isCancelled()) return false;
        int userId = user.getUserInfo().getId();
        try {
            LedgerWalletMutation.execute(
                    user,
                    new EconomyOperation(
                            EconomyOperationId.create("wired-chest-withdraw:" + chestId),
                            userId,
                            userId,
                            "wired_chest_withdraw",
                            "wired.chest.withdraw",
                            EconomyLedger.CREDITS,
                            event.credits,
                            chestId,
                            "Coins taken out of a wired coin chest"));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ChestWithdrawals.class)
                    .error("Could not pay {} coins out of wired chest {} to user {}", credits, chestId, userId, e);
            return false;
        }
        if (user.getClient() != null) user.getClient().sendResponse(new UserCreditsComposer(user));
        return true;
    };

    private static final java.util.concurrent.atomic.AtomicReference<CreditPayer> PAYER =
            new java.util.concurrent.atomic.AtomicReference<>(LEDGER_PAYER);

    /** Replace the purse payment, returning the previous one so a test can put it back. */
    static CreditPayer usePayer(CreditPayer payer) {
        return PAYER.getAndSet(payer == null ? LEDGER_PAYER : payer);
    }

    /** Tell the player their own withdrawal went through (AIR success type 1). */
    public static void confirm(User user) {
        if (user != null && user.getClient() != null) {
            user.getClient()
                    .sendResponse(new WiredTransactionSuccessComposer(WiredTransactionSuccessComposer.TYPE_WITHDRAW));
        }
    }

    /**
     * Put items that left a chest into a player's inventory. They are already theirs in the database,
     * so a player who is not online simply finds them when they log in.
     *
     * @return how many reached the live inventory
     */
    public static int deliver(User user, Collection<Integer> itemIds) {
        if (user == null || itemIds == null || itemIds.isEmpty() || user.getInventory() == null) return 0;

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getItemManager() == null) return 0;

        int delivered = 0;
        int userId = user.getUserInfo().getId();
        for (UserItem item : load(new ArrayList<>(itemIds))) {
            // Only items that are theirs and not placed; a withdrawal made them so.
            if (item.getUserId() != userId || item.getRoomId() != 0) continue;
            user.getInventory().getItemsComponent().addItem(item);
            if (user.getClient() != null) user.getClient().sendResponse(new AddUserItemComposer(item));
            delivered++;
        }
        if (delivered > 0 && user.getClient() != null) user.getClient().sendResponse(new InventoryRefreshComposer());
        return delivered;
    }

    /** {@link #deliver} for callers that hold ids of items that may or may not be the player's. */
    public static int deliverOwned(User user, Collection<Integer> itemIds) {
        return deliver(user, itemIds);
    }

    private static final int LOAD_BATCH = 500;

    /** Load items in batches, so emptying a big chest is a handful of queries rather than thousands. */
    private static List<UserItem> load(List<Integer> itemIds) {
        List<UserItem> items = new ArrayList<>();
        polaris.database.Database database = WiredPlatform.database();
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
                        UserItem item = environment.getItemManager().loadUserItem(set);
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
            InteractionWiredChest chest, Room room, User user, int source, int coins, List<ChestFurniStoredItem> rows) {
        int amount = coins > 0 ? coins : rows.size();
        chest.getContents()
                .addLog(new ChestStorage.LogEntry(
                        "withdraw",
                        System.currentTimeMillis(),
                        user.getUserInfo().getUsername(),
                        amount,
                        0));
        ChestTransactionLog.record(
                room == null ? chest.getRoomId() : room.getId(),
                chest.getId(),
                chest.chestKind(),
                ChestTransactionLog.TYPE_WITHDRAW,
                source,
                user,
                coins > 0 ? ChestStorage.COINS_TYPE : -1,
                amount,
                0,
                coins > 0 ? null : rows);
    }

    private static void afterChange(
            InteractionWiredChest chest, Room room, User user, int source, int amount, int before, int now) {
        if (source == ChestTransactionLog.SOURCE_WIRED) ChestNotifications.wired(chest, room, amount);
        else ChestNotifications.withdrawal(chest, room, user, amount);
        ChestNotifications.afterChange(chest, room, before, now);
    }
}
