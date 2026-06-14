package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class HousekeepingGiveCreditsEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.give_credits";

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
        int amount = this.packet.readInt();

        if (userId <= 0 || !HousekeepingMutationGuard.isPositiveGrantAmount(amount)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (online != null) {
            // giveCredits already pushes UserCreditsComposer and persists via the
            // standard HabboInfo write path; nothing extra needed for the online branch.
            online.giveCredits(amount);
            this.audit(userId, amount);
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1")) {
            statement.setInt(1, amount);
            statement.setInt(2, userId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
                return;
            }
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        this.audit(userId, amount);
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }

    private void audit(int userId, int amount) {
        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "amount=" + amount,
                this.client.getHabbo().getHabboInfo().getIpLogin());
    }
}
