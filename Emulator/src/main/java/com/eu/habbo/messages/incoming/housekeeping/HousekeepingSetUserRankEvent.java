package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;

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
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int userId = this.packet.readInt();
        int rankId = this.packet.readInt();

        if (userId <= 0 || rankId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        PermissionsManager permissions = Emulator.getGameEnvironment().getPermissionsManager();

        if (!permissions.rankExists(rankId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_not_found"));
            return;
        }

        Rank rank = permissions.getRank(rankId);

        if (!HousekeepingTargetRankGuard.canAssignRank(this.client.getHabbo(), rank.getId())) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        int targetRankId;
        if (online != null) {
            targetRankId = online.getHabboInfo().getRank().getId();
        } else {
            targetRankId = 0;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT rank FROM users WHERE id = ? LIMIT 1")) {
                statement.setInt(1, userId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        targetRankId = set.getInt("rank");
                    }
                }
            } catch (SQLException e) {
                this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
                return;
            }
        }

        if (targetRankId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetRank(this.client.getHabbo(), targetRankId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        // Persist for the offline path. Online users get their in-memory
        // HabboInfo.rank rebound below so server-side hasPermission()
        // checks land on the new permission set without a relogin.
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET rank = ? WHERE id = ? LIMIT 1")) {
            statement.setInt(1, rankId);
            statement.setInt(2, userId);
            statement.execute();
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        if (online != null) {
            online.getHabboInfo().setRank(rank);
            // Ship the refreshed permissions snapshot — same payload the
            // :update_permissions command emits when a rank is rebound.
            online.getClient().sendResponse(new UserPermissionsComposer(online));
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "rankId=" + rankId,
                this.client.getHabbo().getHabboInfo().getIpLogin());

        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
