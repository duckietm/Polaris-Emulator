package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.TemporaryRanks;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Sets a user's rank. An optional trailing duration in seconds makes it temporary, the way
 * :perm rank does: the rank lasts that long, then the user gets the rank from before back
 * (TemporaryRanks). 0 or no duration is a lasting rank, which also ends a running timer.
 */
public class HousekeepingSetUserRankEvent extends HousekeepingHandler {
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

        if (!HousekeepingTargetRankGuard.canAssignRank(this.client.getHabbo(), rank.getId())) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
        Habbo online = habboManager.getHabbo(userId);

        int targetRankId;
        if (online != null) {
            targetRankId = online.getHabboInfo().getRank().getId();
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

        if (!HousekeepingTargetRankGuard.canTargetRank(this.client.getHabbo(), targetRankId)) {
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
        } catch (com.eu.habbo.database.SqlQueries.DataAccessException e) {
            // A check that cannot be made refuses the change.
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        int operatorId = this.client.getHabbo().getHabboInfo().getId();
        String operatorName = this.client.getHabbo().getHabboInfo().getUsername();
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
            habboManager.setRank(
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

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                userId,
                "fromRankId=" + targetRankId + " rankId=" + rankId + (temporary ? " until=" + expiresAt : ""),
                this.client.getHabbo().getHabboInfo().getIpLogin());

        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
