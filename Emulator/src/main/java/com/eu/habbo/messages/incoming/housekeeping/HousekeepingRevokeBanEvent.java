package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.modtool.ModToolBanList;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Ends one ban by its id, where the unban action ends every ban of a user.
 * Lets an operator lift an account ban while an IP or machine ban stays.
 */
public class HousekeepingRevokeBanEvent extends MessageHandler {
    static final String ACTION_KEY = "ban.revoke";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        int banId = this.packet.readInt();
        int now = (int) (System.currentTimeMillis() / 1000L);

        if (banId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        int userId = ModToolBanList.activeBanOwner(banId, now);

        if (userId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.no_active_ban"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        if (!ModToolBanList.revoke(banId, now)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.no_active_ban"));
            return;
        }

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_USER,
                userId,
                "",
                "ban=" + banId,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, banId, ""));
    }
}
