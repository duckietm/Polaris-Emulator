package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.WiredPlatform;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final String ITEM_COLUMNS =
            "item_id, extra_data, wired_data, limited_data, guild_id, expires, allow_underpass";

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
                        "INSERT IGNORE INTO wired_chests (item_id, kind, coins, capacity_level, settings) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, chestId);
            statement.setInt(2, kind);
            statement.setInt(3, Math.max(0, coins));
            statement.setInt(4, Math.max(0, capacityLevel));
            statement.setString(5, settings == null ? "{}" : settings);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not create wired chest {}", chestId, e);
        }
    }

    @Override
    public void saveSettings(int chestId, int capacityLevel, String settings) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE wired_chests SET capacity_level = ?, settings = ? WHERE item_id = ?")) {
            statement.setInt(1, Math.max(0, capacityLevel));
            statement.setString(2, settings == null ? "{}" : settings);
            statement.setInt(3, chestId);
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
                                    + ITEM_COLUMNS + ") SELECT id, ?, ?, " + ITEM_COLUMNS
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
            try (PreparedStatement restore = connection.prepareStatement(
                            "INSERT INTO items (id, user_id, room_id, " + ITEM_COLUMNS + ") SELECT id, ?, 0, "
                                    + ITEM_COLUMNS + " FROM wired_chest_items WHERE id = ? AND chest_id = ?");
                    PreparedStatement restoreNewId = connection.prepareStatement(
                            "INSERT INTO items (user_id, room_id, " + ITEM_COLUMNS + ") SELECT ?, 0, " + ITEM_COLUMNS
                                    + " FROM wired_chest_items WHERE id = ? AND chest_id = ?",
                            Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM wired_chest_items WHERE id = ? AND chest_id = ?")) {
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

    private static Connection connection() throws SQLException {
        com.eu.habbo.database.Database database = WiredPlatform.database();
        if (database == null || database.getDataSource() == null) {
            throw new SQLException("The hotel database is not available");
        }
        return database.getDataSource().getConnection();
    }
}
