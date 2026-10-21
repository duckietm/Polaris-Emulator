package com.eu.habbo.habbohotel.rooms.raidprotection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** {@code room_raid_protection} and {@code room_raid_incidents}. Each call is one statement. */
public class RaidProtectionRepository {

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection openConnection() throws SQLException;
    }

    /** What a room's row holds: the settings and the time of its last raid (unix seconds, 0 for none). */
    public record Stored(RaidProtectionSettings settings, int lastRaidAt) {}

    private final ConnectionProvider database;

    public RaidProtectionRepository(ConnectionProvider database) {
        this.database = database;
    }

    /** The room's row, or the defaults when it has none. */
    public Stored load(int roomId) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT enabled, sensitivity, action_type, ban_duration, guard_enabled, guard_duration,"
                                + " guard_sensitivity, last_raid_at FROM room_raid_protection WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return new Stored(RaidProtectionSettings.defaults(roomId), 0);
                RaidProtectionSettings settings = new RaidProtectionSettings(
                        roomId,
                        set.getBoolean("enabled"),
                        set.getInt("sensitivity"),
                        set.getInt("action_type"),
                        set.getInt("ban_duration"),
                        set.getBoolean("guard_enabled"),
                        set.getInt("guard_duration"),
                        set.getInt("guard_sensitivity"));
                return new Stored(
                        settings.isValid() ? settings : RaidProtectionSettings.defaults(roomId),
                        set.getInt("last_raid_at"));
            }
        }
    }

    public void save(RaidProtectionSettings settings, int nowSeconds) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_raid_protection (room_id, enabled, sensitivity, action_type, ban_duration,"
                                + " guard_enabled, guard_duration, guard_sensitivity, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                                + " enabled = VALUES(enabled), sensitivity = VALUES(sensitivity),"
                                + " action_type = VALUES(action_type), ban_duration = VALUES(ban_duration),"
                                + " guard_enabled = VALUES(guard_enabled), guard_duration = VALUES(guard_duration),"
                                + " guard_sensitivity = VALUES(guard_sensitivity), updated_at = VALUES(updated_at)")) {
            statement.setInt(1, settings.roomId());
            statement.setBoolean(2, settings.enabled());
            statement.setInt(3, settings.sensitivity());
            statement.setInt(4, settings.actionType());
            statement.setInt(5, settings.banDurationSeconds());
            statement.setBoolean(6, settings.guardEnabled());
            statement.setInt(7, settings.guardDurationSeconds());
            statement.setInt(8, settings.guardSensitivity());
            statement.setInt(9, nowSeconds);
            statement.executeUpdate();
        }
    }

    /** Records the start of a raid; returns the incident id, 0 when none was generated. */
    public int startIncident(RaidProtectionSettings settings, String trigger, int startedAt) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("INSERT INTO room_raid_protection (room_id, last_raid_at) VALUES (?, ?)"
                            + " ON DUPLICATE KEY UPDATE last_raid_at = VALUES(last_raid_at)")) {
                statement.setInt(1, settings.roomId());
                statement.setInt(2, startedAt);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO room_raid_incidents (room_id, protection_enabled, sensitivity, action_type,"
                            + " trigger_type, started_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, settings.roomId());
                statement.setBoolean(2, settings.enabled());
                statement.setInt(3, settings.sensitivity());
                statement.setInt(4, settings.actionType());
                statement.setString(5, trigger);
                statement.setInt(6, startedAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : 0;
                }
            }
        }
    }

    public void finishIncident(int incidentId, RaidDetector.Summary summary) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE room_raid_incidents SET ended_at = ?, participants = ?, actioned_users = ?,"
                                + " blocked_entries = ? WHERE id = ?")) {
            statement.setInt(1, (int) (summary.endedMs() / 1000L));
            statement.setInt(2, summary.participants());
            statement.setInt(3, summary.actioned());
            statement.setInt(4, summary.blocked());
            statement.setInt(5, incidentId);
            statement.executeUpdate();
        }
    }
}
