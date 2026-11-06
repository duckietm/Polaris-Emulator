package polaris.messages.incoming.housekeeping;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * The gate every housekeeping handler passes through (HousekeepingHandler.allowed): panel
 * access, then the emergency lockdown, which lets only the highest rank in, then the area
 * permission when the handler names one. A refused request gets an immediate answer under
 * {@link #DENIED_ACTION_KEY}, so the client can report it at once instead of waiting for its
 * request timeout.
 */
final class HousekeepingAccess {
    static final String DENIED_ACTION_KEY = "housekeeping.denied";
    static final String DENIED_MESSAGE = "housekeeping.error.no_permission";
    static final String LOCKDOWN_MESSAGE = "housekeeping.error.lockdown";

    private HousekeepingAccess() {}

    static boolean check(GameClient client) {
        return check(client, null);
    }

    static boolean check(GameClient client, String areaPermission) {
        if (!client.getUser().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return deny(client, DENIED_MESSAGE);
        }

        if (HousekeepingLockdown.isLocked() && !HousekeepingTargetRankGuard.isTopRank(client.getUser())) {
            return deny(client, LOCKDOWN_MESSAGE);
        }

        if (areaPermission != null && !client.getUser().hasPermission(areaPermission)) {
            return deny(client, DENIED_MESSAGE);
        }

        return true;
    }

    private static boolean deny(GameClient client, String message) {
        client.sendResponse(new HousekeepingActionResultComposer(DENIED_ACTION_KEY, false, 0, message));

        return false;
    }
}
