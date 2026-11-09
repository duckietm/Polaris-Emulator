package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import polaris.Emulator;
import polaris.hotel.users.User;

/**
 * Apply an arbitrary-duration trade lock. Writes
 * a sanctions row with `trade_locked_until = now + hours*3600` so the lock
 * survives logout/login — that column is the canonical timestamp the
 * mod-tool user-info composer queries on. Online users also get their
 * in-memory UserStats.allowTrade flag cleared so the lock takes
 * effect on the active session without waiting for a relog.
 */
public class HousekeepingTradeLockUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    public static final String ACTION_KEY = "user.trade_lock";

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
        int hours = this.packet.readInt();
        String reason = this.packet.readString();

        this.reply(
                ACTION_KEY,
                tradeLock(
                        this.operator(),
                        userId,
                        hours,
                        reason,
                        () -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)));
    }

    /**
     * Trade-locks a user for {@code hours}, online or not, and alerts an online user with the reason.
     *
     * @param mayTarget the operator's rank guard on the target
     */
    public static HousekeepingResult tradeLock(
            HousekeepingOperator operator, int userId, int hours, String rawReason, BooleanSupplier mayTarget) {
        String reason = HousekeepingInputGuard.normalize(rawReason);

        if (userId <= 0
                || hours <= 0
                || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        if (!mayTarget.getAsBoolean()) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
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
                return HousekeepingResult.fail("housekeeping.error.user_not_found");
            }

            sanction.setInt(1, userId);
            sanction.setString(2, reason);
            sanction.setInt(3, lockedUntil);
            sanction.setInt(4, userId);
            sanction.executeUpdate();
        } catch (SQLException e) {
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        User online = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        if (online != null) {
            online.getUserStats().setAllowTrade(false);

            if (!reason.isEmpty()) {
                online.alert(reason);
            }
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                userId,
                "hours=" + hours + " lockedUntil=" + lockedUntil + " reason="
                        + HousekeepingInputGuard.auditValue(reason),
                operator.ip());
        return HousekeepingResult.ok(userId);
    }
}
