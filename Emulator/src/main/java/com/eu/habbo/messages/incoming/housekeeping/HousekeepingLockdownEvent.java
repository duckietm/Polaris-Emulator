package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Switches the emergency lockdown of the panel. Only the highest rank may, and it is also the
 * only rank the lockdown lets in, so it can always switch it off again.
 */
public class HousekeepingLockdownEvent extends HousekeepingHandler {
    static final String ACTION_KEY = "hotel.lockdown";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        boolean enabled = this.packet.readBoolean();

        if (!HousekeepingTargetRankGuard.isTopRank(this.client.getHabbo())) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (!HousekeepingLockdown.set(enabled)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY + (enabled ? ".on" : ".off"),
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "",
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, enabled ? 1 : 0, ""));
    }
}
