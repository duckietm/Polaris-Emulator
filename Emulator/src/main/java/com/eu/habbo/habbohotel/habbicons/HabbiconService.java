package com.eu.habbo.habbohotel.habbicons;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconStatusComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/** Owns Habbicon entitlement, purchase and collection transitions in one wallet transaction. */
public final class HabbiconService {
    public static final int NOT_OWNED = 0, CLAIMABLE = 1, OWNED = 2, FAVORITE = 3, UNAVAILABLE = 4, REWARD = 5;

    public enum Action {
        BUY,
        BUY_COLLECTION,
        CLAIM,
        FAVORITE,
        UNFAVORITE
    }

    private final DataSource dataSource;

    public HabbiconService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Item(int id, String name, int collectionId, int state, int credits, int points, int pointsType) {
        public boolean owned() {
            return state == OWNED || state == FAVORITE;
        }

        public boolean collected() {
            return state >= CLAIMABLE && state <= FAVORITE;
        }

        public boolean purchasable() {
            return state == NOT_OWNED && (credits > 0 || points > 0);
        }

        private Item withState(int value) {
            return new Item(id, name, collectionId, value, credits, points, pointsType);
        }
    }

    public record Collection(
            int id,
            String name,
            boolean completed,
            int rewardId,
            int rewardState,
            int credits,
            int points,
            int pointsType,
            List<Item> items) {}

    public record Snapshot(
            List<Collection> collections, Map<Integer, Item> items, List<Integer> recent, List<Integer> unseen) {
        public Item requireItem(int id) {
            Item item = items.get(id);
            if (item == null) {
                throw new Rejected(1);
            }
            return item;
        }
    }

    public record Change(Snapshot snapshot, List<Item> changed, Map<Integer, Integer> balances) {}

    public static final class Rejected extends IllegalArgumentException {
        private final int code;

        public Rejected(int code) {
            super("Habbicon action rejected: " + code);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public Snapshot load(int userId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return load(connection, userId);
        }
    }

    private Snapshot load(Connection connection, int userId) throws SQLException {
        Map<Integer, Item> items = new LinkedHashMap<>();
        List<Integer> unseen = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT h.*, u.state, u.unseen, c.reward_id
                FROM habbicons h JOIN habbicon_collections c ON c.id = h.collection_id
                LEFT JOIN users_habbicons u ON u.habbicon_id = h.id AND u.user_id = ?
                ORDER BY h.collection_id, h.id
                """)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int state = rows.getInt("state");
                    if (rows.wasNull()) {
                        state = rows.getBoolean("default_owned")
                                ? OWNED
                                : rows.getInt("reward_id") == rows.getInt("id")
                                        ? REWARD
                                        : !rows.getBoolean("available")
                                                ? UNAVAILABLE
                                                : rows.getInt("cost_credits") == 0 && rows.getInt("cost_points") == 0
                                                        ? CLAIMABLE
                                                        : NOT_OWNED;
                    }
                    Item item = new Item(
                            rows.getInt("id"),
                            rows.getString("name"),
                            rows.getInt("collection_id"),
                            state,
                            rows.getInt("cost_credits"),
                            rows.getInt("cost_points"),
                            rows.getInt("points_type"));
                    items.put(item.id(), item);
                    if (rows.getBoolean("unseen")) {
                        unseen.add(item.id());
                    }
                }
            }
        }
        List<Collection> collections = new ArrayList<>();
        try (PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM habbicon_collections ORDER BY id");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                int id = rows.getInt("id"), rewardId = rows.getInt("reward_id");
                List<Item> members = items.values().stream()
                        .filter(item -> item.collectionId() == id && item.id() != rewardId)
                        .toList();
                boolean complete = !members.isEmpty() && members.stream().allMatch(Item::collected);
                Item reward = items.get(rewardId);
                if (reward != null && reward.collectionId() == id && complete && reward.state() == REWARD) {
                    reward = reward.withState(CLAIMABLE);
                    items.put(rewardId, reward);
                }
                collections.add(new Collection(
                        id,
                        rows.getString("name"),
                        complete,
                        rewardId,
                        reward == null ? UNAVAILABLE : reward.state(),
                        rows.getInt("cost_credits"),
                        rows.getInt("cost_points"),
                        rows.getInt("points_type"),
                        members));
            }
        }
        List<Integer> recent = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT habbicon_id FROM users_habbicons WHERE user_id = ? AND last_used > 0 AND state IN (2, 3)
                ORDER BY last_used DESC, habbicon_id DESC LIMIT 10
                """)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (items.containsKey(rows.getInt(1))) {
                        recent.add(rows.getInt(1));
                    }
                }
            }
        }
        return new Snapshot(
                List.copyOf(collections),
                java.util.Collections.unmodifiableMap(items),
                List.copyOf(recent),
                List.copyOf(unseen));
    }

    public Change change(Habbo habbo, Action action, int id) throws SQLException {
        Change change = LedgerWalletMutation.coordinated(habbo, () -> {
            Change committed = change(habbo.getHabboInfo().getId(), action, id);
            committed
                    .balances()
                    .forEach((currency, balance) -> LedgerWalletMutation.applyCommitted(habbo, currency, balance));
            return committed;
        });
        publish(habbo, change);
        return change;
    }

    public Change grantCatalog(Connection connection, int userId, int id) throws SQLException {
        Snapshot before = load(connection, userId);
        Item item = before.requireItem(id);
        if (item.collected()) {
            throw new Rejected(4);
        }
        if (item.state() != NOT_OWNED) {
            throw new Rejected(1);
        }
        save(connection, userId, id, OWNED, true);
        return changed(connection, userId, before, Map.of());
    }

    public Change change(int userId, Action action, int id) throws SQLException {
        return transact(userId, connection -> {
            Snapshot before = load(connection, userId);
            Map<Integer, Integer> balances = new LinkedHashMap<>();
            if (action == Action.BUY_COLLECTION) {
                Collection collection = before.collections().stream()
                        .filter(set -> set.id() == id)
                        .findFirst()
                        .orElseThrow(() -> new Rejected(1));
                List<Item> missing = collection.items().stream()
                        .filter(item -> !item.collected())
                        .toList();
                if (missing.isEmpty()) {
                    throw new Rejected(4);
                }
                if (collection.credits() <= 0 && collection.points() <= 0
                        || missing.stream().anyMatch(item -> item.state() != NOT_OWNED)) {
                    throw new Rejected(1);
                }
                charge(
                        connection,
                        userId,
                        collection.credits(),
                        collection.points(),
                        collection.pointsType(),
                        balances);
                for (Item item : missing) {
                    save(connection, userId, item.id(), OWNED, true);
                }
            } else {
                Item item = before.requireItem(id);
                int state;
                switch (action) {
                    case BUY -> {
                        if (item.collected()) {
                            throw new Rejected(4);
                        }
                        if (!item.purchasable()) {
                            throw new Rejected(1);
                        }
                        charge(connection, userId, item.credits(), item.points(), item.pointsType(), balances);
                        state = OWNED;
                    }
                    case CLAIM -> {
                        if (item.state() != CLAIMABLE) {
                            throw new Rejected(4);
                        }
                        state = OWNED;
                    }
                    case FAVORITE, UNFAVORITE -> {
                        if (!item.owned()) {
                            throw new Rejected(4);
                        }
                        state = action == Action.FAVORITE ? FAVORITE : OWNED;
                    }
                    default -> throw new Rejected(1);
                }
                save(connection, userId, id, state, action == Action.BUY || action == Action.CLAIM);
            }
            return changed(connection, userId, before, balances);
        });
    }

    private Change changed(Connection connection, int userId, Snapshot before, Map<Integer, Integer> balances)
            throws SQLException {
        Snapshot after = load(connection, userId);
        List<Item> changed = after.items().values().stream()
                .filter(item -> before.requireItem(item.id()).state() != item.state())
                .toList();
        for (Item item : changed) {
            if (item.state() == CLAIMABLE && before.requireItem(item.id()).state() == REWARD) {
                save(connection, userId, item.id(), CLAIMABLE, true);
            }
        }
        return new Change(load(connection, userId), changed, Map.copyOf(balances));
    }

    public boolean use(int userId, int id) throws SQLException {
        return transact(userId, connection -> {
            Item item = load(connection, userId).items().get(id);
            if (item == null || !item.owned()) {
                return false;
            }
            save(connection, userId, id, item.state(), false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE users_habbicons SET last_used =
                        GREATEST(?, (SELECT COALESCE(MAX(recent.last_used), 0) + 1
                            FROM users_habbicons recent WHERE recent.user_id = ?))
                    WHERE user_id = ? AND habbicon_id = ?
                    """)) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setInt(2, userId);
                statement.setInt(3, userId);
                statement.setInt(4, id);
                statement.executeUpdate();
            }
            return true;
        });
    }

    public void clearUnseen(int userId, List<Integer> ids) throws SQLException {
        if (ids.size() > 1000) {
            throw new Rejected(1);
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        ids.isEmpty()
                                ? "UPDATE users_habbicons SET unseen = FALSE WHERE user_id = ?"
                                : "UPDATE users_habbicons SET unseen = FALSE WHERE user_id = ? AND habbicon_id = ?")) {
            statement.setInt(1, userId);
            if (ids.isEmpty()) {
                statement.executeUpdate();
            } else {
                for (int id : ids) {
                    statement.setInt(2, id);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private void charge(
            Connection connection, int userId, int credits, int points, int pointsType, Map<Integer, Integer> balances)
            throws SQLException {
        if (credits < 0 || points < 0 || pointsType < 0) {
            throw new Rejected(1);
        }
        String operationId = EconomyOperationId.create("habbicon:" + userId);
        charge(connection, userId, EconomyLedger.CREDITS, credits, operationId, balances);
        charge(connection, userId, pointsType, points, operationId, balances);
    }

    private void charge(
            Connection connection,
            int userId,
            int currency,
            int amount,
            String operationId,
            Map<Integer, Integer> balances)
            throws SQLException {
        if (amount == 0) {
            return;
        }
        try {
            var result = EconomyLedger.apply(
                    connection,
                    new EconomyOperation(
                            operationId + ":" + currency,
                            userId,
                            userId,
                            "habbicon_purchase",
                            "habbicon.purchase",
                            currency,
                            -amount,
                            null,
                            operationId));
            balances.put(currency, result.balanceAfter());
        } catch (IllegalArgumentException exception) {
            throw new Rejected(currency == EconomyLedger.CREDITS ? 2 : 3);
        }
    }

    private void save(Connection connection, int userId, int id, int state, boolean unseen) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users_habbicons (user_id, habbicon_id, state, unseen) VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE state = VALUES(state), unseen = unseen OR VALUES(unseen)
                """)) {
            statement.setInt(1, userId);
            statement.setInt(2, id);
            statement.setInt(3, state);
            statement.setBoolean(4, unseen);
            statement.executeUpdate();
        }
    }

    private <T> T transact(int userId, Transaction<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    statement.setInt(1, userId);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            throw new Rejected(1);
                        }
                    }
                }
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public static void publish(Habbo habbo, Change change) {
        for (Item item : change.changed()) {
            habbo.getClient().sendResponse(new HabbiconStatusComposer(item.id(), item.state()));
        }
        habbo.getClient().sendResponse(new UserHabbiconsComposer(change.snapshot()));
        int[] unseen =
                change.snapshot().unseen().stream().mapToInt(Integer::intValue).toArray();
        if (unseen.length > 0) {
            habbo.getClient()
                    .sendResponse(new AddHabboItemComposer(unseen, AddHabboItemComposer.AddHabboItemCategory.HABBICON));
        }
        if (change.balances().containsKey(EconomyLedger.CREDITS)) {
            habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
        }
        if (change.balances().keySet().stream().anyMatch(currency -> currency >= 0)) {
            habbo.getClient().sendResponse(new UserCurrencyComposer(habbo));
        }
    }

    @FunctionalInterface
    private interface Transaction<T> {
        T execute(Connection connection) throws SQLException;
    }
}
