package polaris.hotel.modtool;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.database.SqlQueries;

/**
 * The records behind the counts of the mod tool's user info: the user's cautions, their own bans, the bans of
 * the accounts that share their machine and their trade locks. Newest first, at most {@link #LIMIT} rows. Each
 * list is read with the same rule as the count it opens from, so the two agree.
 */
public final class ModToolUserRecords {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModToolUserRecords.class);

    public static final int CAUTIONS = 0;
    public static final int BANS = 1;
    public static final int LINKED_BANS = 2;
    public static final int TRADE_LOCKS = 3;
    public static final int LIMIT = 100;

    /**
     * One row. {@code timestamp} and {@code expires} are unix seconds, 0 when unknown or without an end;
     * {@code username} is the user the row belongs to and {@code staffName} who gave it, empty when unknown.
     */
    public record Entry(int timestamp, String username, String staffName, String type, String reason, int expires) {}

    /** The rows of one list; {@code undetailed} counts the cautions that have no row (0 for the other lists). */
    public record Records(int undetailed, List<Entry> entries) {}

    static final String CAUTIONS_SQL = "SELECT c.created_at, COALESCE(target.username, '') AS username, "
            + "COALESCE(staff.username, '') AS staff_name, c.type, c.reason, c.length_hours "
            + "FROM user_cautions c LEFT JOIN users target ON target.id = c.user_id "
            + "LEFT JOIN users staff ON staff.id = c.staff_id "
            + "WHERE c.user_id = ? ORDER BY c.id DESC LIMIT ?";

    static final String CAUTION_COUNTER_SQL =
            "SELECT cfh_warnings AS total FROM users_settings WHERE user_id = ? LIMIT 1";

    static final String CAUTION_ROWS_SQL = "SELECT COUNT(*) AS total FROM user_cautions WHERE user_id = ?";

    static final String BANS_SQL = "SELECT bans.timestamp, COALESCE(target.username, '') AS username, "
            + "COALESCE(staff.username, '') AS staff_name, bans.type, bans.ban_reason, bans.ban_expire "
            + "FROM bans LEFT JOIN users target ON target.id = bans.user_id "
            + "LEFT JOIN users staff ON staff.id = bans.user_staff_id "
            + "WHERE bans.user_id = ? ORDER BY bans.timestamp DESC, bans.id DESC LIMIT ?";

    /**
     * One row per other account banned on the user's machine - its newest ban there - as the user info counts
     * them: {@code COUNT(DISTINCT user_id)} of the bans on the same non-empty machine id.
     */
    static final String LINKED_BANS_SQL = "SELECT bans.timestamp, COALESCE(target.username, '') AS username, "
            + "COALESCE(staff.username, '') AS staff_name, bans.type, bans.ban_reason, bans.ban_expire "
            + "FROM users me JOIN bans ON bans.machine_id = me.machine_id "
            + "LEFT JOIN users target ON target.id = bans.user_id "
            + "LEFT JOIN users staff ON staff.id = bans.user_staff_id "
            + "WHERE me.id = ? AND me.machine_id <> '' AND bans.user_id <> me.id "
            + "AND bans.id = (SELECT MAX(newest.id) FROM bans newest "
            + "WHERE newest.user_id = bans.user_id AND newest.machine_id = me.machine_id) "
            + "ORDER BY bans.timestamp DESC, bans.id DESC LIMIT ?";

    static final String TRADE_LOCKS_SQL = "SELECT COALESCE(target.username, '') AS username, sanctions.reason, "
            + "sanctions.trade_locked_until FROM sanctions LEFT JOIN users target ON target.id = sanctions.habbo_id "
            + "WHERE sanctions.habbo_id = ? AND sanctions.trade_locked_until > 0 ORDER BY sanctions.id DESC LIMIT ?";

    private ModToolUserRecords() {}

    public static boolean isKind(int kind) {
        return kind >= CAUTIONS && kind <= TRADE_LOCKS;
    }

    /** The list of {@code kind} for {@code userId}; empty when the kind is unknown or the database fails. */
    public static Records load(int userId, int kind) {
        try {
            return switch (kind) {
                case CAUTIONS -> cautions(userId);
                case BANS -> new Records(0, bans(BANS_SQL, userId));
                case LINKED_BANS -> new Records(0, bans(LINKED_BANS_SQL, userId));
                case TRADE_LOCKS -> new Records(0, tradeLocks(userId));
                default -> new Records(0, List.of());
            };
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return new Records(0, List.of());
        }
    }

    private static Records cautions(int userId) {
        List<Entry> entries = SqlQueries.query(
                CAUTIONS_SQL,
                rs -> {
                    int createdAt = rs.getInt("created_at");
                    int hours = rs.getInt("length_hours");
                    return new Entry(
                            createdAt,
                            rs.getString("username"),
                            rs.getString("staff_name"),
                            rs.getString("type"),
                            rs.getString("reason"),
                            cautionExpiry(createdAt, hours));
                },
                userId,
                LIMIT);
        int counter = SqlQueries.queryOne(CAUTION_COUNTER_SQL, rs -> rs.getInt("total"), userId)
                .orElse(0);
        int rows = SqlQueries.queryOne(CAUTION_ROWS_SQL, rs -> rs.getInt("total"), userId)
                .orElse(0);

        return new Records(undetailed(counter, rows), entries);
    }

    private static List<Entry> bans(String sql, int userId) {
        return SqlQueries.query(
                sql,
                rs -> new Entry(
                        rs.getInt("timestamp"),
                        rs.getString("username"),
                        rs.getString("staff_name"),
                        rs.getString("type"),
                        rs.getString("ban_reason"),
                        expiry(rs.getInt("ban_expire"))),
                userId,
                LIMIT);
    }

    private static List<Entry> tradeLocks(int userId) {
        return SqlQueries.query(
                TRADE_LOCKS_SQL,
                rs -> new Entry(
                        0,
                        rs.getString("username"),
                        "",
                        "TRADE_LOCK",
                        rs.getString("reason"),
                        expiry(rs.getInt("trade_locked_until"))),
                userId,
                LIMIT);
    }

    /** The cautions the counter holds beyond the rows found, never negative. */
    static int undetailed(int counter, int rows) {
        return Math.max(0, counter - rows);
    }

    /**
     * An expiry as the wire carries it: 0 for none. A permanent ban or lock is stored as the largest int
     * ({@link ModToolManager#banExpiry} clamps to it), and rows written before that clamp wrapped negative.
     */
    static int expiry(int stored) {
        return stored <= 0 || stored == Integer.MAX_VALUE ? 0 : stored;
    }

    /** The end of a timed caution (a mute or a ban), 0 for one without a length or without a known start. */
    static int cautionExpiry(int createdAt, int lengthHours) {
        if (createdAt <= 0 || lengthHours <= 0) {
            return 0;
        }

        return expiry(ModToolManager.banExpiry(createdAt, ModToolManager.hoursToSeconds(lengthHours)));
    }
}
