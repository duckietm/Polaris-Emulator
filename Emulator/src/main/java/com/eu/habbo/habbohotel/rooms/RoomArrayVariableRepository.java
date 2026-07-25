package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

final class RoomArrayVariableRepository {
    private static final Type FIELD_MAP_TYPE = new TypeToken<Map<Integer, Long>>() {}.getType();
    private final RoomDependencies.ConnectionProvider database;

    RoomArrayVariableRepository(DataSource dataSource) {
        this(dataSource::getConnection);
    }

    RoomArrayVariableRepository(RoomDependencies.ConnectionProvider database) {
        this.database = Objects.requireNonNull(database);
    }

    StoredValue load(RoomArrayVariableManager.Key key) throws SQLException {
        String sql = """
                SELECT array_value.logical_length, array_value.version, entry.entry_index, entry.entry_data
                FROM room_wired_array_values array_value
                LEFT JOIN room_wired_array_entries entry
                  ON entry.room_id = array_value.room_id
                 AND entry.variable_item_id = array_value.variable_item_id
                 AND entry.owner_type = array_value.owner_type
                 AND entry.owner_id = array_value.owner_id
                WHERE array_value.room_id = ? AND array_value.variable_item_id = ?
                  AND array_value.owner_type = ? AND array_value.owner_id = ?
                ORDER BY entry.entry_index
                """;
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key, 1);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;
                int logicalLength = set.getInt("logical_length");
                long version = set.getLong("version");
                Map<Integer, Map<Integer, Long>> entries = new LinkedHashMap<>();
                do {
                    int entryIndex = set.getInt("entry_index");
                    if (set.wasNull()) continue;
                    String raw = set.getString("entry_data");
                    Map<Integer, Long> fields = WiredManager.getGson().fromJson(raw, FIELD_MAP_TYPE);
                    entries.put(entryIndex, fields == null ? Map.of() : fields);
                } while (set.next());
                return new StoredValue(logicalLength, version, entries);
            }
        }
    }

    long replace(RoomArrayVariableManager.Key key, long expectedVersion, WiredArrayValue value, int now)
            throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureHeader(connection, key, now);
                long currentVersion = lockVersion(connection, key);
                if (currentVersion != expectedVersion) {
                    connection.rollback();
                    return -1L;
                }

                long nextVersion = Math.addExact(currentVersion, 1L);
                updateHeader(connection, key, value.getLogicalLength(), nextVersion, now);
                deleteEntries(connection, key);
                insertEntries(connection, key, value.entriesSnapshot());
                connection.commit();
                return nextVersion;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    boolean delete(RoomArrayVariableManager.Key key, long expectedVersion) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Long currentVersion = lockExistingVersion(connection, key);
                if (currentVersion == null || currentVersion != expectedVersion) {
                    connection.rollback();
                    return false;
                }
                deleteEntries(connection, key);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM room_wired_array_values WHERE room_id = ? AND variable_item_id = ? AND owner_type = ? AND owner_id = ?")) {
                    bindKey(statement, key, 1);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    boolean hasDefinition(int roomId, int definitionItemId) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1
                        FROM room_wired_array_values array_value
                        WHERE array_value.room_id = ? AND array_value.variable_item_id = ?
                          AND (array_value.logical_length > 0 OR EXISTS (
                              SELECT 1 FROM room_wired_array_entries entry
                              WHERE entry.room_id = array_value.room_id
                                AND entry.variable_item_id = array_value.variable_item_id
                                AND entry.owner_type = array_value.owner_type
                                AND entry.owner_id = array_value.owner_id))
                        LIMIT 1
                        """)) {
            statement.setInt(1, roomId);
            statement.setInt(2, definitionItemId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    void deleteDefinition(int roomId, int definitionItemId) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement entries = connection.prepareStatement(
                            "DELETE FROM room_wired_array_entries WHERE room_id = ? AND variable_item_id = ?");
                    PreparedStatement values = connection.prepareStatement(
                            "DELETE FROM room_wired_array_values WHERE room_id = ? AND variable_item_id = ?")) {
                entries.setInt(1, roomId);
                entries.setInt(2, definitionItemId);
                entries.executeUpdate();
                values.setInt(1, roomId);
                values.setInt(2, definitionItemId);
                values.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    void deleteOwner(int roomId, int ownerType, int ownerId) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement entries = connection.prepareStatement(
                            "DELETE FROM room_wired_array_entries WHERE room_id = ? AND owner_type = ? AND owner_id = ?");
                    PreparedStatement values = connection.prepareStatement(
                            "DELETE FROM room_wired_array_values WHERE room_id = ? AND owner_type = ? AND owner_id = ?")) {
                entries.setInt(1, roomId);
                entries.setInt(2, ownerType);
                entries.setInt(3, ownerId);
                entries.executeUpdate();
                values.setInt(1, roomId);
                values.setInt(2, ownerType);
                values.setInt(3, ownerId);
                values.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private static void ensureHeader(Connection connection, RoomArrayVariableManager.Key key, int now)
            throws SQLException {
        String sql = """
                INSERT INTO room_wired_array_values
                    (room_id, variable_item_id, owner_type, owner_id, logical_length, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, 0, ?, ?)
                ON DUPLICATE KEY UPDATE variable_item_id = VALUES(variable_item_id)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key, 1);
            statement.setInt(5, now);
            statement.setInt(6, now);
            statement.executeUpdate();
        }
    }

    private static long lockVersion(Connection connection, RoomArrayVariableManager.Key key) throws SQLException {
        Long version = lockExistingVersion(connection, key);
        if (version == null) throw new SQLException("Array value header disappeared during mutation.");
        return version;
    }

    private static Long lockExistingVersion(Connection connection, RoomArrayVariableManager.Key key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM room_wired_array_values WHERE room_id = ? AND variable_item_id = ? AND owner_type = ? AND owner_id = ? FOR UPDATE")) {
            bindKey(statement, key, 1);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getLong(1) : null;
            }
        }
    }

    private static void updateHeader(
            Connection connection, RoomArrayVariableManager.Key key, int logicalLength, long version, int now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE room_wired_array_values SET logical_length = ?, version = ?, updated_at = ? WHERE room_id = ? AND variable_item_id = ? AND owner_type = ? AND owner_id = ?")) {
            statement.setInt(1, logicalLength);
            statement.setLong(2, version);
            statement.setInt(3, now);
            bindKey(statement, key, 4);
            if (statement.executeUpdate() != 1) throw new SQLException("Array value header update failed.");
        }
    }

    private static void deleteEntries(Connection connection, RoomArrayVariableManager.Key key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM room_wired_array_entries WHERE room_id = ? AND variable_item_id = ? AND owner_type = ? AND owner_id = ?")) {
            bindKey(statement, key, 1);
            statement.executeUpdate();
        }
    }

    private static void insertEntries(
            Connection connection, RoomArrayVariableManager.Key key, Map<Integer, WiredArrayEntry> entries)
            throws SQLException {
        if (entries.isEmpty()) return;
        String sql = """
                INSERT INTO room_wired_array_entries
                    (room_id, variable_item_id, owner_type, owner_id, entry_index, entry_data)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<Integer, WiredArrayEntry> entry : entries.entrySet()) {
                bindKey(statement, key, 1);
                statement.setInt(5, entry.getKey());
                statement.setString(
                        6, WiredManager.getGson().toJson(entry.getValue().valuesByFieldId()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void bindKey(PreparedStatement statement, RoomArrayVariableManager.Key key, int offset)
            throws SQLException {
        statement.setInt(offset, key.roomId());
        statement.setInt(offset + 1, key.definitionItemId());
        statement.setInt(offset + 2, key.ownerType());
        statement.setInt(offset + 3, key.ownerId());
    }

    record StoredValue(int logicalLength, long version, Map<Integer, Map<Integer, Long>> entries) {}
}
