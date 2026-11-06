package polaris.messages.incoming.housekeeping;

import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolBanList;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Ends one ban by its id, where the unban action ends every ban of a user.
 * Lets an operator lift an account ban while an IP or machine ban stays.
 */
public class HousekeepingRevokeBanEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.BANS;
    }

    static final String ACTION_KEY = "ban.revoke";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
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

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
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
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_USER,
                userId,
                "",
                "ban=" + banId,
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, banId, ""));
    }
}
