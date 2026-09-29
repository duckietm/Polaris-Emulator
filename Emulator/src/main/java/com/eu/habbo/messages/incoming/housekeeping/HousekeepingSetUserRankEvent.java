package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HousekeepingSetUserRankEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.set_rank";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        int userId = this.packet.readInt();
        int rankId = this.packet.readInt();

        if (userId <= 0 || rankId <= 0) {
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
            habboManager.setRank(userId, rank.getId());
        } catch (Exception e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                userId,
                "fromRankId=" + targetRankId + " rankId=" + rankId,
                this.client.getHabbo().getHabboInfo().getIpLogin());

        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
