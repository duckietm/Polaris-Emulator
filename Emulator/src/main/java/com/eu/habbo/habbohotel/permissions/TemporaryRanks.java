package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ranks given for a while (table user_temporary_ranks): VIP for 30 days, event staff for a
 * weekend. When the time is up the user gets the rank they had before, online or offline, checked
 * once a minute. Giving a rank any other way ends the timer, so a promotion is never undone.
 */
public final class TemporaryRanks {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryRanks.class);

    /** The {@code via} of a rank change this class makes; any other via ends the timer. */
    public static final String VIA_PREFIX = "temporary";

    public static final String VIA_ENDED = VIA_PREFIX + " rank ended";
    static final long SWEEP_MS = 60_000L;
    private static final int SWEEP_BATCH = 100;

    public record Row(int userId, int rankId, int previousRankId, int expiresAt, String setByName, String reason) {}

    private final AtomicBoolean started = new AtomicBoolean();

    /** Overrides that ran out up to this time have had their users refreshed. */
    private volatile int overridesCheckedUntil;

    /** Starts the once-a-minute check; later calls do nothing. */
    void start(ThreadPooling threading) {
        if (threading != null && this.started.compareAndSet(false, true)) {
            this.schedule(threading);
        }
    }

    private void schedule(ThreadPooling threading) {
        threading.run(
                () -> {
                    try {
                        if (WiredPlatform.isReady()) {
                            this.sweep(WiredPlatform.unixTimestamp());
                        }
                    } finally {
                        this.schedule(threading);
                    }
                },
                SWEEP_MS);
    }

    void sweep(int now) {
        this.endTemporaryRanks(now);
        this.refreshExpiredOverrides(now);
    }

    /** Gives every user whose time is up their previous rank back. */
    private void endTemporaryRanks(int now) {
        List<Row> due;

        try {
            due = SqlQueries.query(
                    "SELECT user_id, rank_id, previous_rank_id, expires_at, set_by_name, reason FROM user_temporary_ranks"
                            + " WHERE expires_at <= ? ORDER BY expires_at LIMIT " + SWEEP_BATCH,
                    TemporaryRanks::row,
                    now);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to read temporary ranks", e);
            return;
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();

        for (Row row : due) {
            int backTo =
                    environment.getPermissionsManager().rankExists(row.previousRankId()) ? row.previousRankId() : 1;

            try {
                environment
                        .getHabboManager()
                        .setRank(row.userId(), backTo, PermissionAuditLog.SYSTEM, "system", VIA_ENDED);
            } catch (Exception e) {
                LOGGER.error("Failed to end the temporary rank of user {}", row.userId(), e);
            }

            clear(row.userId());
        }
    }

    /**
     * A timed override that ran out changes what the client may show: drop the cached values and
     * send online users their permissions and perks again.
     */
    void refreshExpiredOverrides(int now) {
        int from = overrideCheckStart(this.overridesCheckedUntil, now);
        this.overridesCheckedUntil = now;

        List<Integer> users;

        try {
            users = SqlQueries.query(
                    "SELECT DISTINCT user_id FROM user_permission_overrides WHERE expires_at > ? AND expires_at <= ?",
                    rs -> rs.getInt("user_id"),
                    from,
                    now);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to read expired permission overrides", e);
            return;
        }

        GameEnvironment environment = WiredPlatform.gameEnvironment();

        for (int userId : users) {
            environment.getPermissionsManager().getOverrides().invalidate(userId);

            Habbo online = environment.getHabboManager().getHabbo(userId);

            if (online != null && online.getClient() != null) {
                online.getClient().sendResponse(new UserPermissionsComposer(online));
                online.getClient().sendResponse(new UserPerksComposer(online));
            }
        }
    }

    /** The first check looks two sweeps back; later ones start where the last ended. */
    static int overrideCheckStart(int checkedUntil, int now) {
        return checkedUntil > 0 ? checkedUntil : now - (int) (SWEEP_MS / 1000L) * 2;
    }

    /**
     * Records a temporary rank. A user who already has one keeps the rank from before the first,
     * so stacking temporary ranks still ends on their real rank.
     */
    public static void give(
            int userId, int rankId, int previousRankId, int expiresAt, int actorId, String actorName, String reason) {
        SqlQueries.update(
                "INSERT INTO user_temporary_ranks (user_id, rank_id, previous_rank_id, expires_at, set_by, set_by_name, reason, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE rank_id = VALUES(rank_id), expires_at = VALUES(expires_at),"
                        + " set_by = VALUES(set_by), set_by_name = VALUES(set_by_name), reason = VALUES(reason)",
                userId,
                rankId,
                previousRankId,
                expiresAt,
                actorId,
                actorName == null ? "" : actorName,
                reason == null ? "" : reason,
                WiredPlatform.unixTimestamp());
    }

    public static Optional<Row> find(int userId) {
        if (WiredPlatform.database() == null) {
            return Optional.empty();
        }

        try {
            return SqlQueries.queryOne(
                    "SELECT user_id, rank_id, previous_rank_id, expires_at, set_by_name, reason FROM user_temporary_ranks"
                            + " WHERE user_id = ? LIMIT 1",
                    TemporaryRanks::row,
                    userId);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to read the temporary rank of user {}", userId, e);
            return Optional.empty();
        }
    }

    public static void clear(int userId) {
        if (WiredPlatform.database() == null) {
            return;
        }

        try {
            SqlQueries.update("DELETE FROM user_temporary_ranks WHERE user_id = ? LIMIT 1", userId);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to clear the temporary rank of user {}", userId, e);
        }
    }

    /** Whether a rank change made with this via keeps the timer (only this class's own). */
    public static boolean keepsTimer(String via) {
        return via != null && via.startsWith(VIA_PREFIX);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(
                rs.getInt("user_id"),
                rs.getInt("rank_id"),
                rs.getInt("previous_rank_id"),
                rs.getInt("expires_at"),
                rs.getString("set_by_name"),
                rs.getString("reason"));
    }
}
