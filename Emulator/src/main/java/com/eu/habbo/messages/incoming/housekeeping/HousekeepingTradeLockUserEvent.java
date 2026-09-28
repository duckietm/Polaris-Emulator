package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Apply an arbitrary-duration trade lock. Writes
 * a sanctions row with `trade_locked_until = now + hours*3600` so the lock
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
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        int userId = this.packet.readInt();
        int hours = this.packet.readInt();
        String reason = HousekeepingInputGuard.normalize(this.packet.readString());

        if (userId <= 0
                || hours <= 0
                || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int duration = HousekeepingSanctionDuration.secondsFromHours(hours);
        int lockedUntil = HousekeepingSanctionDuration.unixUntil(Emulator.getIntUnixTimestamp(), duration);

        // The lock is what the mod tools write: a sanctions row carrying trade_locked_until,
        // which login turns into can_trade again, plus can_trade itself for the offline user.
        // The row repeats the latest level and probation so the sanction ladder is not reset.
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement block = connection.prepareStatement(
                        "UPDATE users_settings SET can_trade = '0' WHERE user_id = ? LIMIT 1");
                PreparedStatement sanction = connection.prepareStatement(
                        "INSERT INTO sanctions (habbo_id, sanction_level, probation_timestamp, reason, trade_locked_until, is_muted, mute_duration) "
                                + "SELECT ?, COALESCE(MAX(latest.sanction_level), 0), COALESCE(MAX(latest.probation_timestamp), 0), ?, ?, 0, 0 "
                                + "FROM (SELECT sanction_level, probation_timestamp FROM sanctions WHERE habbo_id = ? ORDER BY id DESC LIMIT 1) latest")) {
            block.setInt(1, userId);

            if (block.executeUpdate() == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(
                        ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
                return;
            }

            sanction.setInt(1, userId);
            sanction.setString(2, reason);
            sanction.setInt(3, lockedUntil);
            sanction.setInt(4, userId);
            sanction.executeUpdate();
        } catch (SQLException e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
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
                ACTION_KEY,
                userId,
                "hours=" + hours + " lockedUntil=" + lockedUntil + " reason="
                        + HousekeepingInputGuard.auditValue(reason),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
