package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChestStore} on the hotel database ({@code wired_chests} and {@code wired_chest_items}).
 *
 * <p>Every move runs in its own transaction and guards on where the row is now: an item is only
 * taken from an inventory it is still in, and only handed out of the chest it is still in. Two
 * players (or a player and a wired effect) racing for the same item therefore cannot both get it.
 */
public final class DatabaseChestStore implements ChestStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseChestStore.class);

    private static final int LOOKUP_BATCH = 500;

    private static final String ITEM_COLUMNS =
            "item_id, extra_data, wired_data, limited_data, guild_id, expires, allow_underpass";

    // The same columns read from items, where older hotels allow NULL in the text columns; the chest table does
    // not, so a NULL becomes the chest table's default.
    private static final String ITEM_SOURCE_COLUMNS = "item_id, COALESCE(extra_data, ''), COALESCE(wired_data, ''), "
            + "COALESCE(limited_data, '0:0'), COALESCE(guild_id, 0), COALESCE(expires, 0), COALESCE(allow_underpass, 0)";

    /** Where the store gets its connections: the hotel database, or a test's. */
    @FunctionalInterface
    interface ConnectionSource {
        Connection open() throws SQLException;
    }

    private final ConnectionSource connections;

    public DatabaseChestStore() {
        this(DatabaseChestStore::hotelConnection);
    }

    DatabaseChestStore(ConnectionSource connections) {
        this.connections = connections;
    }

    @Override
    public ChestRow loadChest(int chestId) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT kind, coins, capacity_level, settings FROM wired_chests WHERE item_id = ?")) {
            statement.setInt(1, chestId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;
                return new ChestRow(
                        chestId,
                        set.getInt("kind"),
                        set.getInt("coins"),
                        set.getInt("capacity_level"),
                        set.getString("settings"));
            }
        } catch (SQLException e) {
            LOGGER.error("Could not load wired chest {}", chestId, e);
            return null;
        }
    }

    @Override
    public void createChest(int chestId, int kind, int coins, int capacityLevel, String settings) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO wired_chests (item_id, kind, coins, capacity_level, settings, owner_id)"
                                + " VALUES (?, ?, ?, ?, ?, COALESCE((SELECT i.user_id FROM items i WHERE i.id = ?), 0))")) {
            statement.setInt(1, chestId);
            statement.setInt(2, kind);
            statement.setInt(3, Math.max(0, coins));
            statement.setInt(4, Math.max(0, capacityLevel));
            statement.setString(5, settings == null ? "{}" : settings);
            statement.setInt(6, chestId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not create wired chest {}", chestId, e);
        }
    }

    @Override
    public void saveSettings(int chestId, int capacityLevel, String settings) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE wired_chests SET capacity_level = ?, settings = ?, owner_id = COALESCE((SELECT i.user_id"
                                + " FROM items i WHERE i.id = ? AND i.user_id > 0), owner_id) WHERE item_id = ?")) {
            // The owner is recorded on every save, so a chest whose item vanishes without being
            // released can still be handed back (see ChestRelease#sweepOrphans).
            statement.setInt(1, Math.max(0, capacityLevel));
            statement.setString(2, settings == null ? "{}" : settings);
            statement.setInt(3, chestId);
            statement.setInt(4, chestId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not save wired chest {} settings", chestId, e);
        }
    }

    @Override
    public boolean addCoins(int chestId, int delta) {
        if (delta == 0) return true;

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE wired_chests SET coins = coins + ? WHERE item_id = ? AND coins + ? >= 0")) {
            statement.setInt(1, delta);
            statement.setInt(2, chestId);
            statement.setInt(3, delta);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Could not change the coins of wired chest {} by {}", chestId, delta, e);
            return false;
        }
    }

    @Override
    public List<StoredRow> loadItems(int chestId) {
        List<StoredRow> rows = new ArrayList<>();
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("SELECT id, sequence, " + ITEM_COLUMNS
                        + " FROM wired_chest_items WHERE chest_id = ? ORDER BY sequence, id")) {
            statement.setInt(1, chestId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    rows.add(new StoredRow(
                            set.getInt("id"),
                            set.getInt("item_id"),
                            set.getString("extra_data"),
                            set.getString("wired_data"),
                            set.getString("limited_data"),
                            set.getInt("guild_id"),
                            set.getInt("expires"),
                            set.getInt("allow_underpass") == 1,
                            set.getLong("sequence")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not load the furni of wired chest {}", chestId, e);
        }
        return rows;
    }

    @Override
    public List<Integer> depositItems(int chestId, int fromUserId, List<Integer> itemIds, long sequence) {
        List<Integer> moved = new ArrayList<>();
        if (itemIds == null || itemIds.isEmpty()) return moved;

        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement copy =
                            connection.prepareStatement("INSERT INTO wired_chest_items (id, chest_id, sequence, "
                                    + ITEM_COLUMNS + ") SELECT id, ?, ?, " + ITEM_SOURCE_COLUMNS
                                    + " FROM items WHERE id = ? AND user_id = ? AND room_id = 0");
                    PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0")) {
                for (int itemId : itemIds) {
                    copy.setInt(1, chestId);
                    copy.setLong(2, sequence);
                    copy.setInt(3, itemId);
                    copy.setInt(4, fromUserId);
                    if (copy.executeUpdate() != 1) continue;

                    delete.setInt(1, itemId);
                    delete.setInt(2, fromUserId);
                    if (delete.executeUpdate() != 1) {
                        throw new SQLException("Item " + itemId + " changed hands during a chest deposit");
                    }
                    moved.add(itemId);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                moved.clear();
                LOGGER.error("Wired chest {} deposit rolled back", chestId, e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            moved.clear();
            LOGGER.error("Wired chest {} deposit failed", chestId, e);
        }
        return moved;
    }

    @Override
    public Map<Integer, Integer> withdrawItems(int chestId, int toUserId, List<Integer> itemIds) {
        Map<Integer, Integer> moved = new LinkedHashMap<>();
        if (itemIds == null || itemIds.isEmpty()) return moved;

        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                moved.putAll(moveOut(connection, chestId, toUserId, itemIds));
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                moved.clear();
                LOGGER.error("Wired chest {} withdrawal rolled back", chestId, e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            moved.clear();
            LOGGER.error("Wired chest {} withdrawal failed", chestId, e);
        }
        return moved;
    }

    @Override
    public boolean redeemCreditFurni(int chestId, int fromUserId, List<Integer> itemIds, int coins) {
        if (itemIds == null || itemIds.isEmpty() || coins <= 0) return false;

        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0");
                    PreparedStatement credit = connection.prepareStatement(
                            "UPDATE wired_chests SET coins = coins + ? WHERE item_id = ?")) {
                for (int itemId : itemIds) {
                    delete.setInt(1, itemId);
                    delete.setInt(2, fromUserId);
                    if (delete.executeUpdate() != 1) {
                        throw new SQLException("Credit furni " + itemId + " is no longer in the inventory");
                    }
                }
                credit.setInt(1, coins);
                credit.setInt(2, chestId);
                if (credit.executeUpdate() != 1) throw new SQLException("Wired chest " + chestId + " has no row");
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                LOGGER.error("Wired chest {} credit redemption rolled back", chestId, e);
                return false;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.error("Wired chest {} credit redemption failed", chestId, e);
            return false;
        }
    }

    /**
     * Move stored items back into {@code items} as the player's, on the caller's transaction. Each row
     * is claimed by deleting it from the chest, so only one caller can hand it out.
     */
    private static Map<Integer, Integer> moveOut(
            Connection connection, int chestId, int toUserId, List<Integer> itemIds) throws SQLException {
        Map<Integer, Integer> moved = new LinkedHashMap<>();
        try (PreparedStatement restore = connection.prepareStatement(
                        "INSERT INTO items (id, user_id, room_id, " + ITEM_COLUMNS + ") SELECT id, ?, 0, "
                                + ITEM_COLUMNS + " FROM wired_chest_items WHERE id = ? AND chest_id = ?");
                PreparedStatement restoreNewId = connection.prepareStatement(
                        "INSERT INTO items (user_id, room_id, " + ITEM_COLUMNS + ") SELECT ?, 0, " + ITEM_COLUMNS
                                + " FROM wired_chest_items WHERE id = ? AND chest_id = ?",
                        Statement.RETURN_GENERATED_KEYS);
                PreparedStatement delete =
                        connection.prepareStatement("DELETE FROM wired_chest_items WHERE id = ? AND chest_id = ?")) {
            for (int itemId : itemIds) {
                // Claim the row first: only the caller whose delete removes it hands it out.
                int newId = itemId;
                boolean restored;
                try {
                    restore.setInt(1, toUserId);
                    restore.setInt(2, itemId);
                    restore.setInt(3, chestId);
                    restored = restore.executeUpdate() == 1;
                } catch (SQLIntegrityConstraintViolationException taken) {
                    // The id was handed to another item meanwhile; the item gets a new one.
                    restoreNewId.setInt(1, toUserId);
                    restoreNewId.setInt(2, itemId);
                    restoreNewId.setInt(3, chestId);
                    restored = restoreNewId.executeUpdate() == 1;
                    if (restored) {
                        try (ResultSet keys = restoreNewId.getGeneratedKeys()) {
                            newId = keys.next() ? keys.getInt(1) : -1;
                        }
                    }
                }
                if (!restored) continue;

                delete.setInt(1, itemId);
                delete.setInt(2, chestId);
                if (delete.executeUpdate() != 1 || newId <= 0) {
                    throw new SQLException("Item " + itemId + " left wired chest " + chestId + " twice");
                }
                moved.put(itemId, newId);
            }
        }
        return moved;
    }

    @Override
    public Release releaseChest(int chestId, int ownerId) {
        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Release release = release(connection, chestId, ownerId);
                if (release.rowsRemoved()) connection.commit();
                else connection.rollback();
                return release;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                LOGGER.error("Wired chest {} could not be released to its owner; its contents stay stored", chestId, e);
                return Release.of(ReleaseStatus.FAILED, chestId, ownerId);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.error("Wired chest {} could not be released to its owner; its contents stay stored", chestId, e);
            return Release.of(ReleaseStatus.FAILED, chestId, ownerId);
        }
    }

    /**
     * {@link #releaseChest} on the caller's transaction: the caller commits when rows were removed and
     * rolls back otherwise.
     */
    static Release release(Connection connection, int chestId, int ownerHint) throws SQLException {
        int coins;
        int recordedOwner;
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT coins, owner_id FROM wired_chests WHERE item_id = ? FOR UPDATE")) {
            statement.setInt(1, chestId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return Release.of(ReleaseStatus.NO_CHEST, chestId, ownerHint);
                coins = set.getInt("coins");
                recordedOwner = set.getInt("owner_id");
            }
        }

        List<Integer> stored = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM wired_chest_items WHERE chest_id = ? ORDER BY sequence, id FOR UPDATE")) {
            statement.setInt(1, chestId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) stored.add(set.getInt("id"));
            }
        }

        if (stored.isEmpty() && coins <= 0) {
            deleteChestRow(connection, chestId);
            return Release.of(ReleaseStatus.EMPTY, chestId, ownerHint);
        }

        int owner = resolveOwner(connection, chestId, ownerHint, recordedOwner);
        if (owner <= 0) {
            LOGGER.warn(
                    "Wired chest {} holds {} furni and {} coins but has no known owner; its rows are kept",
                    chestId,
                    stored.size(),
                    coins);
            return Release.of(ReleaseStatus.OWNER_UNKNOWN, chestId, 0);
        }

        Map<Integer, Integer> moved = moveOut(connection, chestId, owner, stored);
        if (moved.size() != stored.size()) {
            throw new SQLException("Only " + moved.size() + " of " + stored.size() + " furni left wired chest "
                    + chestId + " for its owner");
        }

        int paid = 0;
        int creditsAfter = -1;
        if (coins > 0) {
            EconomyMutationResult refund =
                    EconomyLedger.apply(connection, ChestRelease.refundOperation(chestId, owner, coins));
            paid = refund.applied() ? coins : 0;
            creditsAfter = refund.balanceAfter();
        }

        deleteChestRow(connection, chestId);
        return new Release(ReleaseStatus.RELEASED, chestId, owner, moved, paid, creditsAfter);
    }

    private static void deleteChestRow(Connection connection, int chestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_chests WHERE item_id = ?")) {
            statement.setInt(1, chestId);
            statement.executeUpdate();
        }
    }

    /**
     * The caller's owner, else the chest item's owner, else the owner recorded on the chest: the first
     * one that is a user that exists, or 0.
     */
    private static int resolveOwner(Connection connection, int chestId, int ownerHint, int recordedOwner)
            throws SQLException {
        if (userExists(connection, ownerHint)) return ownerHint;

        try (PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM items WHERE id = ?")) {
            statement.setInt(1, chestId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    int itemOwner = set.getInt("user_id");
                    if (userExists(connection, itemOwner)) return itemOwner;
                }
            }
        }
        return userExists(connection, recordedOwner) ? recordedOwner : 0;
    }

    private static boolean userExists(Connection connection, int userId) throws SQLException {
        if (userId <= 0) return false;
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    @Override
    public Set<Integer> chestIdsAmong(Collection<Integer> itemIds) {
        Set<Integer> chests = new HashSet<>();
        if (itemIds == null || itemIds.isEmpty()) return chests;

        List<Integer> ids =
                itemIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        try (Connection connection = connection()) {
            for (int from = 0; from < ids.size(); from += LOOKUP_BATCH) {
                List<Integer> batch = ids.subList(from, Math.min(from + LOOKUP_BATCH, ids.size()));
                String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id FROM wired_chests WHERE item_id IN (" + placeholders + ")")) {
                    for (int i = 0; i < batch.size(); i++) statement.setInt(i + 1, batch.get(i));
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) chests.add(set.getInt("item_id"));
                    }
                }
            }
        } catch (SQLException e) {
            // Unknown counts as "may be a chest": releasing an item that is no chest changes nothing.
            LOGGER.error("Could not look up wired chests among {} item(s)", ids.size(), e);
            chests.addAll(ids);
        }
        return chests;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records the current owner of every chest whose item still exists first, so the owner of a
     * chest whose item disappears later is known.
     */
    @Override
    public List<OrphanRow> orphanedChests() {
        List<OrphanRow> orphans = new ArrayList<>();
        try (Connection connection = connection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("UPDATE wired_chests c JOIN items i ON i.id = c.item_id"
                            + " SET c.owner_id = i.user_id WHERE i.user_id > 0 AND c.owner_id <> i.user_id")) {
                statement.executeUpdate();
            }
            // An item parked in wired_chest_items is not gone, so it is never taken for an orphan.
            try (PreparedStatement statement = connection.prepareStatement("SELECT c.item_id, c.owner_id"
                            + " FROM wired_chests c LEFT JOIN items i ON i.id = c.item_id WHERE i.id IS NULL"
                            + " AND NOT EXISTS (SELECT 1 FROM wired_chest_items s WHERE s.id = c.item_id)"
                            + " ORDER BY c.item_id");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) orphans.add(new OrphanRow(set.getInt("item_id"), set.getInt("owner_id")));
            }
        } catch (SQLException e) {
            LOGGER.error("Could not look for wired chests whose item is gone", e);
        }
        return orphans;
    }

    private Connection connection() throws SQLException {
        return this.connections.open();
    }

    private static Connection hotelConnection() throws SQLException {
        com.eu.habbo.database.Database database = WiredPlatform.database();
        if (database == null || database.getDataSource() == null) {
            throw new SQLException("The hotel database is not available");
        }
        return database.getDataSource().getConnection();
    }
}
