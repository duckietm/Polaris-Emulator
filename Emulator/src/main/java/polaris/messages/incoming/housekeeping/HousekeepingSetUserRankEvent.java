package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.permissions.TemporaryRanks;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;

/**
 * Sets a user's rank. An optional trailing duration in seconds makes it temporary, the way
 * :perm rank does: the rank lasts that long, then the user gets the rank from before back
 * (TemporaryRanks). 0 or no duration is a lasting rank, which also ends a running timer.
 */
public class HousekeepingSetUserRankEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.PERMISSIONS;
    }

    public static final String ACTION_KEY = "user.set_rank";

    /** A temporary rank lasts at most a year; longer is a lasting rank. */
    static final int MAX_TEMPORARY_SECONDS = 365 * 24 * 3600;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();
        int rankId = this.packet.readInt();
        int durationSeconds = 0;

        if (this.packet.bytesAvailable() > 0) {
            durationSeconds = this.packet.readInt();
        }

        this.reply(
                ACTION_KEY,
                setRank(
                        this.operator(),
                        userId,
                        rankId,
                        durationSeconds,
                        rank -> HousekeepingTargetRankGuard.canAssignRank(this.client.getUser(), rank.getId()),
                        targetRankId ->
                                HousekeepingTargetRankGuard.canTargetRank(this.client.getUser(), targetRankId)));
    }

    /**
     * Gives a user a rank, for good or for {@code durationSeconds} (1 to a year) when that is above 0.
     *
     * @param mayAssign     the operator's rank guard on the rank given
     * @param mayTargetRank the operator's rank guard on the user's current rank
     */
    public static HousekeepingResult setRank(
            HousekeepingOperator operator,
            int userId,
            int rankId,
            int durationSeconds,
            Predicate<Rank> mayAssign,
            IntPredicate mayTargetRank) {
        if (userId <= 0 || rankId <= 0 || durationSeconds < 0 || durationSeconds > MAX_TEMPORARY_SECONDS) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        if (!permissions.rankExists(rankId)) {
            return HousekeepingResult.fail("housekeeping.error.rank_not_found");
        }

        Rank rank = permissions.getRank(rankId);

        if (!mayAssign.test(rank)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        UserManager userManager = Emulator.getGameEnvironment().getUserManager();
        User online = userManager.getUser(userId);

        int targetRankId;
        if (online != null) {
            targetRankId = online.getUserInfo().getRank().getId();
        } else {
            targetRankId = 0;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT rank FROM users WHERE id = ? LIMIT 1")) {
                statement.setInt(1, userId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        targetRankId = set.getInt("rank");
                    }
                }
            } catch (SQLException e) {
                return HousekeepingResult.fail("housekeeping.error.db_failed");
            }
        }

        if (targetRankId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.user_not_found");
        }

        if (!mayTargetRank.test(targetRankId)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        // The same path as :give_rank and RCON: saves the rank and, for an online user, swaps the rank
        // badge and effect and resends permissions, perks and the mod tool, and tells plugins.
        try {
            if (HousekeepingLockoutGuard.rankChangeLocksOut(userId, targetRankId, rank.getId())) {
                return HousekeepingResult.fail("housekeeping.error.lockout");
            }
        } catch (polaris.database.SqlQueries.DataAccessException e) {
            // A check that cannot be made refuses the change.
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        int operatorId = operator.id();
        String operatorName = operator.name();
        boolean temporary = durationSeconds > 0;
        int expiresAt = temporary ? (int) (System.currentTimeMillis() / 1000L) + durationSeconds : 0;

        try {
            if (temporary) {
                // A second temporary rank still ends on the rank from before the first.
                int previousRankId = TemporaryRanks.find(userId)
                        .map(TemporaryRanks.Row::previousRankId)
                        .orElse(targetRankId);

                TemporaryRanks.give(
                        userId, rank.getId(), previousRankId, expiresAt, operatorId, operatorName, "housekeeping");
            }

            // A temporary change keeps the timer; any other ends it (TemporaryRanks.keepsTimer).
            userManager.setRank(
                    userId,
                    rank.getId(),
                    operatorId,
                    operatorName,
                    temporary ? TemporaryRanks.VIA_PREFIX + " housekeeping" : "housekeeping");
        } catch (Exception e) {
            if (temporary) TemporaryRanks.clear(userId);

            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                userId,
                "fromRankId=" + targetRankId + " rankId=" + rankId + (temporary ? " until=" + expiresAt : ""),
                operator.ip());

        return HousekeepingResult.ok(userId);
    }
}
