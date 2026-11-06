package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.permissions.TemporaryRanks;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

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

    private static final String ACTION_KEY = "user.set_rank";

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

        if (userId <= 0 || rankId <= 0 || durationSeconds < 0 || durationSeconds > MAX_TEMPORARY_SECONDS) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        if (!permissions.rankExists(rankId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_not_found"));
            return;
        }

        Rank rank = permissions.getRank(rankId);

        if (!HousekeepingTargetRankGuard.canAssignRank(this.client.getUser(), rank.getId())) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
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
                this.client.sendResponse(
                        new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
                return;
            }
        }

        if (targetRankId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetRank(this.client.getUser(), targetRankId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        // The same path as :give_rank and RCON: saves the rank and, for an online user, swaps the rank
        // badge and effect and resends permissions, perks and the mod tool, and tells plugins.
        try {
            if (HousekeepingLockoutGuard.rankChangeLocksOut(userId, targetRankId, rank.getId())) {
                this.client.sendResponse(
                        new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.lockout"));
                return;
            }
        } catch (polaris.database.SqlQueries.DataAccessException e) {
            // A check that cannot be made refuses the change.
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        int operatorId = this.client.getUser().getUserInfo().getId();
        String operatorName = this.client.getUser().getUserInfo().getUsername();
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

            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "fromRankId=" + targetRankId + " rankId=" + rankId + (temporary ? " until=" + expiresAt : ""),
                this.client.getUser().getUserInfo().getIpLogin());

        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
