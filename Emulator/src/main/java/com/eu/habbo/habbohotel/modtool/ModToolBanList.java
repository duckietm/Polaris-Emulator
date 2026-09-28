package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.database.SqlQueries;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bans still in force, newest first, with the banned user's and the staff member's names. */
public final class ModToolBanList {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModToolBanList.class);

    public record Entry(
            int userId,
            String username,
            String type,
            String reason,
            int expires,
            String staffName,
            int timestamp,
            String ip) {}

    private ModToolBanList() {}

    public static List<Entry> active(int now, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT bans.user_id, COALESCE(target.username, '') AS username, bans.type, bans.ban_reason, "
                            + "bans.ban_expire, COALESCE(staff.username, '') AS staff_name, bans.timestamp, bans.ip "
                            + "FROM bans LEFT JOIN users target ON target.id = bans.user_id "
                            + "LEFT JOIN users staff ON staff.id = bans.user_staff_id "
                            + "WHERE bans.ban_expire > ? ORDER BY bans.timestamp DESC LIMIT ?",
                    rs -> new Entry(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("type"),
                            rs.getString("ban_reason"),
                            rs.getInt("ban_expire"),
                            rs.getString("staff_name"),
                            rs.getInt("timestamp"),
                            rs.getString("ip")),
                    now,
                    limit);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return List.of();
        }
    }
}
