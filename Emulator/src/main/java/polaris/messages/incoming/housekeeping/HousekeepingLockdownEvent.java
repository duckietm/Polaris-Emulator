package polaris.messages.incoming.housekeeping;

import java.util.function.BooleanSupplier;
import polaris.hotel.modtool.HousekeepingAuditLog;

/**
 * Switches the emergency lockdown of the panel. Only the highest rank may, and it is also the
 * only rank the lockdown lets in, so it can always switch it off again.
 */
public class HousekeepingLockdownEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.HOTEL;
    }

    public static final String ACTION_KEY = "hotel.lockdown";

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

        this.reply(
                ACTION_KEY,
                set(this.operator(), enabled, () -> HousekeepingTargetRankGuard.isTopRank(this.client.getUser())));
    }

    /**
     * Switches the lockdown on or off and stores it. On success the result's id is 1 for on, 0 for off.
     *
     * @param isTopRank whether the operator holds the highest rank
     */
    public static HousekeepingResult set(HousekeepingOperator operator, boolean enabled, BooleanSupplier isTopRank) {
        if (!isTopRank.getAsBoolean()) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        if (!HousekeepingLockdown.set(enabled)) {
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY + (enabled ? ".on" : ".off"),
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "",
                operator.ip());
        return HousekeepingResult.ok(enabled ? 1 : 0);
    }

    /** Whether the lockdown is on. */
    public static boolean isEnabled() {
        return HousekeepingLockdown.isLocked();
    }
}
