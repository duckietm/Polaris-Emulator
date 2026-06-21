package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Apply an arbitrary-duration trade lock. Writes
 * `users_settings.trade_locked_until = now + hours*3600` so the lock
 * survives logout/login — that column is the canonical timestamp the
 * mod-tool user-info composer queries on. Online users also get their
 * in-memory HabboStats.allowTrade flag cleared so the lock takes
 * effect on the active session without waiting for a relog.
 */
public class HousekeepingTradeLockUserEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.trade_lock";

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
        int hours = this.packet.readInt();
        String reason = HousekeepingInputGuard.normalize(this.packet.readString());

        if (userId <= 0 || hours <= 0 || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int duration = HousekeepingSanctionDuration.secondsFromHours(hours);
        int lockedUntil = HousekeepingSanctionDuration.unixUntil(Emulator.getIntUnixTimestamp(), duration);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET trade_locked_until = ? WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, lockedUntil);
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

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (online != null) {
            online.getHabboStats().setAllowTrade(false);

            if (!reason.isEmpty()) {
                online.alert(reason);
            }
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "hours=" + hours + " lockedUntil=" + lockedUntil + " reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
