package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The permission audit (table permission_audit): every rank a user is given, and every rank or key
 * value a permission reload finds changed in the database, with who did it and how. Rows are only
 * added, never changed. Writes run off the calling thread.
 */
public final class PermissionAuditLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionAuditLog.class);

    public static final String RANK_SET = "rank_set";
    public static final String PERMISSION_CHANGED = "permission_changed";
    public static final String TARGET_USER = "user";
    public static final String TARGET_RANK = "rank";
    /** The actor of a change nobody in the hotel made (startup, RCON, a plugin). */
    public static final int SYSTEM = 0;

    private static final int MAX_TEXT = 64;
    private static final String INSERT = "INSERT INTO permission_audit"
            + " (timestamp, actor_id, actor_name, action, target_type, target_id, subject, old_value, new_value, via)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public record Entry(
            int timestamp,
            int actorId,
            String actorName,
            String action,
            String targetType,
            int targetId,
            String subject,
            String oldValue,
            String newValue,
            String via) {}

    private PermissionAuditLog() {}

    public static void rankSet(int actorId, String actorName, int userId, int oldRankId, int newRankId, String via) {
        write(List.of(new Entry(
                WiredPlatform.unixTimestamp(),
                actorId,
                actorName,
                RANK_SET,
                TARGET_USER,
                userId,
                "rank",
                oldRankId > 0 ? String.valueOf(oldRankId) : PermissionChange.NONE,
                String.valueOf(newRankId),
                via)));
    }

    public static void permissionChanges(int actorId, String actorName, List<PermissionChange> changes, String via) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        int now = WiredPlatform.unixTimestamp();
        List<Entry> entries = new ArrayList<>(changes.size());

        for (PermissionChange change : changes) {
            entries.add(new Entry(
                    now,
                    actorId,
                    actorName,
                    PERMISSION_CHANGED,
                    TARGET_RANK,
                    change.rankId(),
                    change.key(),
                    change.oldValue(),
                    change.newValue(),
                    via));
        }

        write(entries);
    }

    /** Newest first; a user id above 0 keeps only that user's rank changes. */
    public static List<Entry> recent(int userId, int limit) {
        if (WiredPlatform.database() == null) {
            return List.of();
        }

        int rows = Math.max(1, Math.min(50, limit));
        String sql =
                "SELECT timestamp, actor_id, actor_name, action, target_type, target_id, subject, old_value, new_value, via"
                        + " FROM permission_audit"
                        + (userId > 0 ? " WHERE target_type = 'user' AND target_id = ?" : "")
                        + " ORDER BY id DESC LIMIT " + rows;

        try {
            SqlQueries.RowMapper<Entry> mapper = rs -> new Entry(
                    rs.getInt("timestamp"),
                    rs.getInt("actor_id"),
                    rs.getString("actor_name"),
                    rs.getString("action"),
                    rs.getString("target_type"),
                    rs.getInt("target_id"),
                    rs.getString("subject"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    rs.getString("via"));
            return userId > 0 ? SqlQueries.query(sql, mapper, userId) : SqlQueries.query(sql, mapper);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to read the permission audit", e);
            return List.of();
        }
    }

    private static void write(List<Entry> entries) {
        if (WiredPlatform.database() == null) {
            return;
        }

        Runnable task = () -> {
            try {
                SqlQueries.batchUpdate(INSERT, entries, (ps, entry) -> {
                    ps.setInt(1, entry.timestamp());
                    ps.setInt(2, entry.actorId());
                    ps.setString(3, clip(entry.actorName()));
                    ps.setString(4, entry.action());
                    ps.setString(5, entry.targetType());
                    ps.setInt(6, entry.targetId());
                    ps.setString(7, clip(entry.subject()));
                    ps.setString(8, clip(entry.oldValue()));
                    ps.setString(9, clip(entry.newValue()));
                    ps.setString(10, clip(entry.via()));
                });
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Failed to write the permission audit", e);
            }
        };

        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) {
            threading.run(task);
        } else {
            task.run();
        }
    }

    static String clip(String value) {
        if (value == null) {
            return "";
        }

        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
