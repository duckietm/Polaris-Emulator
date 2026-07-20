package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

final class RoomUserVariableRepository {
    private static final String FIND_BY_USER_SQL = """
            SELECT variable_item_id, value, created_at, updated_at
            FROM room_user_wired_variables
            WHERE room_id = ? AND user_id = ?
            """;
    private static final String UPSERT_SQL =
            "INSERT INTO room_user_wired_variables (room_id, user_id, variable_item_id, value, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), "
                    + "updated_at = VALUES(updated_at)";

    private final DataSource dataSource;

    RoomUserVariableRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<StoredAssignment> findByUser(int roomId, int userId) throws SQLException {
        List<StoredAssignment> assignments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int rawValue = set.getInt("value");
                    Integer value = set.wasNull() ? null : rawValue;
                    assignments.add(new StoredAssignment(
                            set.getInt("variable_item_id"),
                            value,
                            set.getInt("created_at"),
                            set.getInt("updated_at")));
                }
            }
        }
        return assignments;
    }

    void upsert(
            int roomId,
            int userId,
            int definitionItemId,
            Integer value,
            int createdAt,
            int updatedAt)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            if (value == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, value);
            }
            statement.setInt(5, createdAt);
            statement.setInt(6, updatedAt);
            statement.executeUpdate();
        }
    }

    void delete(int roomId, int userId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM room_user_wired_variables "
                                + "WHERE room_id = ? AND user_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, definitionItemId);
            statement.executeUpdate();
        }
    }

    void deleteDefinition(int roomId, int definitionItemId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM room_user_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, definitionItemId);
            statement.executeUpdate();
        }
    }

    record StoredAssignment(int definitionItemId, Integer value, int createdAt, int updatedAt) {}
}
