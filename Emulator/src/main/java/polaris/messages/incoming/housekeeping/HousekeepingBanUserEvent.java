package polaris.messages.incoming.housekeeping;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.modtool.ModToolBanType;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Apply an arbitrary-duration account ban. Duration is taken in hours
 * from the wire and converted to seconds for ModToolManager.ban —
 * unlike ModToolSanctionBanEvent which only accepts the four fixed
 * Habbo-protocol banType buckets.
 */
public class HousekeepingBanUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.BANS;
    }

    private static final String ACTION_KEY = "user.ban";

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
        int hours = this.packet.readInt();

        if (userId <= 0
                || hours <= 0
                || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int duration = HousekeepingSanctionDuration.secondsFromHours(hours);

        List<ModToolBan> bans = Emulator.getGameEnvironment()
                .getModToolManager()
                .ban(userId, this.client.getUser(), reason, duration, ModToolBanType.ACCOUNT, 0);

        if (bans == null || bans.isEmpty()) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.ban_failed"));
            return;
        }

        // ModToolBan doesn't expose the `bans` table autoinc id on the
        // object, so we return the target user id as the actionId — it's
        // the only stable handle the client can use until a dedicated
        // housekeeping_log row id supersedes it.
        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "hours=" + hours + " reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
