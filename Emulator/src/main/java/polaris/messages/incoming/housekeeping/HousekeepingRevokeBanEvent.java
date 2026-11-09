package polaris.messages.incoming.housekeeping;

import java.util.function.IntPredicate;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolBanList;

/**
 * Ends one ban by its id, where the unban action ends every ban of a user.
 * Lets an operator lift an account ban while an IP or machine ban stays.
 */
public class HousekeepingRevokeBanEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.BANS;
    }

    public static final String ACTION_KEY = "ban.revoke";

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

        this.reply(
                ACTION_KEY,
                revoke(
                        this.operator(),
                        banId,
                        owner -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), owner)));
    }

    /**
     * Ends one active ban by its id. On success the result's id is the ban id.
     *
     * @param mayTargetOwner the operator's rank guard on the banned user
     */
    public static HousekeepingResult revoke(HousekeepingOperator operator, int banId, IntPredicate mayTargetOwner) {
        int now = (int) (System.currentTimeMillis() / 1000L);

        if (banId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        int userId = ModToolBanList.activeBanOwner(banId, now);

        if (userId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.no_active_ban");
        }

        if (!mayTargetOwner.test(userId)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        if (!ModToolBanList.revoke(banId, now)) {
            return HousekeepingResult.fail("housekeeping.error.no_active_ban");
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_USER,
                userId,
                "",
                "ban=" + banId,
                operator.ip());
        return HousekeepingResult.ok(banId);
    }
}
