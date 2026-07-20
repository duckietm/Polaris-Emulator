package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

final class RoomRepository {

    private static final String FIND_WIRED_SETTINGS_SQL =
            "SELECT inspect_mask, modify_mask FROM room_wired_settings " + "WHERE room_id = ? LIMIT 1";
    private static final String SAVE_WIRED_SETTINGS_SQL =
            "INSERT INTO room_wired_settings (room_id, inspect_mask, modify_mask) "
                    + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "inspect_mask = VALUES(inspect_mask), modify_mask = VALUES(modify_mask)";
    private static final String UPDATE_USER_COUNT_SQL = "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1";
    private static final String UPSERT_CUSTOM_LAYOUT_SQL = "INSERT INTO room_models_custom "
            + "(id, name, door_x, door_y, door_dir, heightmap) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
            + "door_x = ?, door_y = ?, door_dir = ?, heightmap = ?";
    private static final String RECORD_ENTRY_SQL =
            "INSERT INTO room_enter_log (room_id, user_id, timestamp) VALUES(?, ?, ?)";
    private static final String RECORD_EXIT_SQL = "UPDATE room_enter_log SET exit_timestamp = ? "
            + "WHERE user_id = ? AND room_id = ? "
            + "ORDER BY timestamp DESC LIMIT 1";
    private static final String RECORD_VOTE_SQL = "INSERT INTO room_votes (user_id, room_id) VALUES (?, ?)";

    private final RoomDependencies.ConnectionProvider database;

    RoomRepository(RoomDependencies.ConnectionProvider database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    WiredSettings findWiredSettings(int roomId) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_WIRED_SETTINGS_SQL)) {
            statement.setInt(1, roomId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new WiredSettings(resultSet.getInt("inspect_mask"), resultSet.getInt("modify_mask"));
                }
            }
        }

        return WiredSettings.defaults();
    }

    void saveWiredSettings(int roomId, int inspectMask, int modifyMask) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(SAVE_WIRED_SETTINGS_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, inspectMask);
            statement.setInt(3, modifyMask);
            statement.executeUpdate();
        }
    }

    void updateUserCount(int roomId, int userCount) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_USER_COUNT_SQL)) {
            statement.setInt(1, userCount);
            statement.setInt(2, roomId);
            statement.executeUpdate();
        }
    }

    void upsertCustomLayout(int roomId, String map, int doorX, int doorY, int doorDirection) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPSERT_CUSTOM_LAYOUT_SQL)) {
            statement.setInt(1, roomId);
            statement.setString(2, "custom_" + roomId);
            statement.setInt(3, doorX);
            statement.setInt(4, doorY);
            statement.setInt(5, doorDirection);
            statement.setString(6, map);
            statement.setInt(7, doorX);
            statement.setInt(8, doorY);
            statement.setInt(9, doorDirection);
            statement.setString(10, map);
            statement.execute();
        }
    }

    void recordEntry(int roomId, int userId, int timestamp) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(RECORD_ENTRY_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, userId);
            statement.setInt(3, timestamp);
            statement.execute();
        }
    }

    void recordExit(int roomId, int userId, int timestamp) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(RECORD_EXIT_SQL)) {
            statement.setInt(1, timestamp);
            statement.setInt(2, userId);
            statement.setInt(3, roomId);
            statement.execute();
        }
    }

    void recordVote(int roomId, int userId) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(RECORD_VOTE_SQL)) {
            statement.setInt(1, userId);
            statement.setInt(2, roomId);
            statement.execute();
        }
    }

    record WiredSettings(int inspectMask, int modifyMask) {

        static WiredSettings defaults() {
            return new WiredSettings(Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK, Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK);
        }
    }
}
