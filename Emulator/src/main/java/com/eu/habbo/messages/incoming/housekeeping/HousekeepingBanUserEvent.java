package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.util.List;

/**
 * Apply an arbitrary-duration account ban. Duration is taken in hours
 * from the wire and converted to seconds for ModToolManager.ban —
 * unlike ModToolSanctionBanEvent which only accepts the four fixed
 * Habbo-protocol banType buckets.
 */
public class HousekeepingBanUserEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.ban";

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
        String reason = HousekeepingInputGuard.normalize(this.packet.readString());
        int hours = this.packet.readInt();

        if (userId <= 0 || hours <= 0 || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int duration = HousekeepingSanctionDuration.secondsFromHours(hours);

        List<ModToolBan> bans = Emulator.getGameEnvironment().getModToolManager()
                .ban(userId, this.client.getHabbo(), reason, duration, ModToolBanType.ACCOUNT, 0);

        if (bans == null || bans.isEmpty()) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.ban_failed"));
            return;
        }

        // ModToolBan doesn't expose the `bans` table autoinc id on the
        // object, so we return the target user id as the actionId — it's
        // the only stable handle the client can use until a dedicated
        // housekeeping_log row id supersedes it.
        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "hours=" + hours + " reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
