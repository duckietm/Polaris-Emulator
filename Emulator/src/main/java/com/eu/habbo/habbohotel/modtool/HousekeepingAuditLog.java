package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Append-only audit trail for privileged housekeeping/admin actions (rank grants,
 * currency grants, etc.). Writes are dispatched off the calling thread; the
 * backing table is created on first use so no manual migration is required.
 */
public final class HousekeepingAuditLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingAuditLog.class);

    private static volatile boolean tableReady = false;

    private HousekeepingAuditLog() {
    }

    /**
     * Records a privileged action asynchronously.
     *
     * @param operatorId   the acting staff member's user id
     * @param operatorName the acting staff member's username
     * @param action       a short action key, e.g. {@code "user.set_rank"}
     * @param targetUserId the affected user's id (0 if not applicable)
     * @param detail       free-form detail, e.g. {@code "rankId=6"} (capped to 512 chars)
     * @param ip           the operator's IP, for correlation
     */
    public static void log(int operatorId, String operatorName, String action, int targetUserId, String detail, String ip) {
        Emulator.getThreading().run(() -> writeEntry(operatorId, operatorName, action, targetUserId, detail, ip));
    }

    private static void writeEntry(int operatorId, String operatorName, String action, int targetUserId, String detail, String ip) {
        ensureTable();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO housekeeping_log (timestamp, actor_id, actor_name, target_type, target_id, target_label, action, detail, success) " +
                             "VALUES (?, ?, ?, 'user', ?, '', ?, ?, 1)")) {
            statement.setInt(1, Emulator.getIntUnixTimestamp());
            statement.setInt(2, operatorId);
            statement.setString(3, operatorName != null ? operatorName : "");
            statement.setInt(4, targetUserId);
            statement.setString(5, action != null ? action : "");
            statement.setString(6, truncate(detail, ip));
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Failed to write housekeeping audit log entry", e);
        }
    }

    private static String truncate(String detail, String ip) {
        String value = detail == null ? "" : detail;
        if (ip != null && !ip.isEmpty()) {
            value = value.isEmpty() ? "ip=" + ip : value + " ip=" + ip;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static void ensureTable() {
        if (tableReady) {
            return;
        }
        synchronized (HousekeepingAuditLog.class) {
            if (tableReady) {
                return;
            }
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(
                        "CREATE TABLE IF NOT EXISTS housekeeping_log (" +
                                "id INT NOT NULL AUTO_INCREMENT, " +
                                "timestamp INT NOT NULL, " +
                                "actor_id INT NOT NULL, " +
                                "actor_name VARCHAR(64) NOT NULL DEFAULT '', " +
                                "target_type VARCHAR(16) NOT NULL DEFAULT 'user', " +
                                "target_id INT NOT NULL DEFAULT 0, " +
                                "target_label VARCHAR(128) NOT NULL DEFAULT '', " +
                                "action VARCHAR(64) NOT NULL DEFAULT '', " +
                                "detail VARCHAR(500) NOT NULL DEFAULT '', " +
                                "success TINYINT NOT NULL DEFAULT 1, " +
                                "PRIMARY KEY (id), " +
                                "KEY timestamp (timestamp)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                tableReady = true;
            } catch (SQLException e) {
                LOGGER.error("Failed to create housekeeping_log table", e);
            }
        }
    }
}
