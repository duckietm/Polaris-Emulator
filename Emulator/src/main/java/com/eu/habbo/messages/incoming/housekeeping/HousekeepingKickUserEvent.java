package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Kick a user out of their current room. Mirrors ModToolManager.kick
 * (leave room + alert), but the legacy method gates on ACC_SUPPORTTOOL,
 * which would force HK operators to also hold the support-tool permission.
 * Replicating the few lines locally keeps the HK module self-gated on
 * ACC_HOUSEKEEPING.
 */
public class HousekeepingKickUserEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.kick";

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

        if (userId <= 0 || !HousekeepingInputGuard.isWithinLimit(reason, HousekeepingInputGuard.MAX_REASON_LENGTH)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (target == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_offline"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (target.hasPermission(Permission.ACC_UNKICKABLE)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.target_unkickable"));
            return;
        }

        if (target.getHabboInfo().getCurrentRoom() != null) {
            Emulator.getGameEnvironment().getRoomManager().leaveRoom(target, target.getHabboInfo().getCurrentRoom());
        }

        if (!reason.isEmpty()) {
            target.alert(reason);
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "reason=" + HousekeepingInputGuard.auditValue(reason),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
