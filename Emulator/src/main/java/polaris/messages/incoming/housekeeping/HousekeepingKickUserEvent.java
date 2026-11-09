package polaris.messages.incoming.housekeeping;

import java.util.function.BooleanSupplier;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/**
 * Kick a user out of their current room. Mirrors ModToolManager.kick
 * (leave room + alert), but the legacy method gates on ACC_SUPPORTTOOL,
 * which would force HK operators to also hold the support-tool permission.
 * Replicating the few lines locally keeps the HK module self-gated on
 * ACC_HOUSEKEEPING. The work is {@link #kick}, shared with the RCON
 * housekeeping command.
 */
public class HousekeepingKickUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    public static final String ACTION_KEY = "user.kick";

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
        String reason = this.packet.readString();

        this.reply(
                ACTION_KEY,
                kick(
                        this.operator(),
                        userId,
                        reason,
                        () -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)));
    }

    /**
     * Kicks an online user from their room and alerts them with the reason, if any.
     *
     * @param mayTarget the operator's rank guard on the target, checked once the target is online
     */
    public static HousekeepingResult kick(
            HousekeepingOperator operator, int userId, String rawReason, BooleanSupplier mayTarget) {
        String reason = HousekeepingInputGuard.normalize(rawReason);

        if (userId <= 0 || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        if (target == null) {
            return HousekeepingResult.fail("housekeeping.error.user_offline");
        }

        if (!mayTarget.getAsBoolean()) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        if (target.hasPermission(Permission.ACC_UNKICKABLE)) {
            return HousekeepingResult.fail("housekeeping.error.target_unkickable");
        }

        if (target.getUserInfo().getCurrentRoom() != null) {
            Emulator.getGameEnvironment()
                    .getRoomManager()
                    .leaveRoom(target, target.getUserInfo().getCurrentRoom());
        }

        if (!reason.isEmpty()) {
            target.alert(reason);
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                userId,
                "reason=" + HousekeepingInputGuard.auditValue(reason),
                operator.ip());
        return HousekeepingResult.ok(userId);
    }
}
