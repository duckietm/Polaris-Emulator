package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Kick a user out of their current room. Mirrors ModToolManager.kick
 * (leave room + alert), but the legacy method gates on ACC_SUPPORTTOOL,
 * which would force HK operators to also hold the support-tool permission.
 * Replicating the few lines locally keeps the HK module self-gated on
 * ACC_HOUSEKEEPING.
 */
public class HousekeepingKickUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    private static final String ACTION_KEY = "user.kick";

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
        String reason = HousekeepingInputGuard.normalize(this.packet.readString());

        if (userId <= 0 || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        if (target == null) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_offline"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (target.hasPermission(Permission.ACC_UNKICKABLE)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.target_unkickable"));
            return;
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
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
