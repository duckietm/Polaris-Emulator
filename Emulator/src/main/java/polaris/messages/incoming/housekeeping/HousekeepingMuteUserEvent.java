package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Apply an arbitrary-duration in-room mute. User.mute is a session-only
 * mute (it stores remaining seconds on the live Habbo object), so the
 * target must be online for the action to take effect — when the target
 * isn't online the handler returns ok=false with `user_offline` so the
 * UI can fall back to ModToolSanctionMute or surface a clear error.
 */
public class HousekeepingMuteUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    private static final String ACTION_KEY = "user.mute";

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
        int minutes = this.packet.readInt();

        if (userId <= 0
                || minutes <= 0
                || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
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

        target.mute(HousekeepingSanctionDuration.secondsFromMinutes(minutes), false);

        if (!reason.isEmpty()) {
            target.alert(reason);
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "minutes=" + minutes + " reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
