package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

final class RoomRepository {

    private static final String FIND_WIRED_SETTINGS_SQL =
            "SELECT inspect_mask, modify_mask, timezone FROM room_wired_settings " + "WHERE room_id = ? LIMIT 1";
    private static final String SAVE_WIRED_SETTINGS_SQL =
            "INSERT INTO room_wired_settings (room_id, inspect_mask, modify_mask, timezone) "
                    + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "inspect_mask = VALUES(inspect_mask), modify_mask = VALUES(modify_mask), "
                    + "timezone = VALUES(timezone)";
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
    private static final String FIND_RAID_PROTECTION_SQL = "SELECT enabled, detection_sensitivity, action_type, "
            + "ban_duration_seconds, guard_enabled, guard_duration_seconds, guard_sensitivity, last_raid_at "
            + "FROM room_raid_protection WHERE room_id = ? LIMIT 1";
    private static final String SAVE_RAID_PROTECTION_SQL = "INSERT INTO room_raid_protection "
            + "(room_id, enabled, detection_sensitivity, action_type, ban_duration_seconds, "
            + "guard_enabled, guard_duration_seconds, guard_sensitivity) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
            + "enabled = VALUES(enabled), detection_sensitivity = VALUES(detection_sensitivity), "
            + "action_type = VALUES(action_type), ban_duration_seconds = VALUES(ban_duration_seconds), "
            + "guard_enabled = VALUES(guard_enabled), guard_duration_seconds = VALUES(guard_duration_seconds), "
            + "guard_sensitivity = VALUES(guard_sensitivity)";
    private static final String RECORD_RAID_SQL =
            "UPDATE room_raid_protection SET last_raid_at = ? WHERE room_id = ? LIMIT 1";

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
                    String timezone = resultSet.getString("timezone");
                    return new WiredSettings(
                            resultSet.getInt("inspect_mask"),
                            resultSet.getInt("modify_mask"),
                            (timezone != null) ? timezone : "");
                }
            }
        }

        return WiredSettings.defaults();
    }

    void saveWiredSettings(int roomId, int inspectMask, int modifyMask, String timezone) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(SAVE_WIRED_SETTINGS_SQL)) {
            statement.setInt(1, roomId);
            statement.setInt(2, inspectMask);
            statement.setInt(3, modifyMask);
            statement.setString(4, (timezone != null) ? timezone : "");
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

    RaidProtection findRaidProtection(int roomId) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_RAID_PROTECTION_SQL)) {
            statement.setInt(1, roomId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new RaidProtection(
                            new RaidProtectionSettings(
                                    roomId,
                                    resultSet.getBoolean("enabled"),
                                    resultSet.getInt("detection_sensitivity"),
                                    resultSet.getInt("action_type"),
                                    resultSet.getInt("ban_duration_seconds"),
                                    resultSet.getBoolean("guard_enabled"),
                                    resultSet.getInt("guard_duration_seconds"),
                                    resultSet.getInt("guard_sensitivity")),
                            resultSet.getInt("last_raid_at"));
                }
            }
        }

        return new RaidProtection(RaidProtectionSettings.defaults(roomId), 0);
    }

    void saveRaidProtection(RaidProtectionSettings settings) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(SAVE_RAID_PROTECTION_SQL)) {
            statement.setInt(1, settings.getRoomId());
            statement.setBoolean(2, settings.isEnabled());
            statement.setInt(3, settings.getDetectionSensitivity());
            statement.setInt(4, settings.getActionType());
            statement.setInt(5, settings.getBanDurationSeconds());
            statement.setBoolean(6, settings.isGuardEnabled());
            statement.setInt(7, settings.getGuardDurationSeconds());
            statement.setInt(8, settings.getGuardSensitivity());
            statement.executeUpdate();
        }
    }

    void recordRaid(int roomId, int atSeconds) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(RECORD_RAID_SQL)) {
            statement.setInt(1, atSeconds);
            statement.setInt(2, roomId);
            statement.executeUpdate();
        }
    }

    record WiredSettings(int inspectMask, int modifyMask, String timezone) {

        static WiredSettings defaults() {
            return new WiredSettings(Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK, Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK, "");
        }
    }

    /** The stored settings plus the one piece of history the client shows next to them. */
    record RaidProtection(RaidProtectionSettings settings, int lastRaidAtSeconds) {}
}
