package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * The permission gate every housekeeping handler starts with. A refused
 * request gets an immediate answer under {@link #DENIED_ACTION_KEY}, so the
 * client can report it at once instead of waiting for its request timeout.
 */
final class HousekeepingAccess {
    static final String DENIED_ACTION_KEY = "housekeeping.denied";
    static final String DENIED_MESSAGE = "housekeeping.error.no_permission";

    private HousekeepingAccess() {}

    static boolean check(GameClient client) {
        if (client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return true;
        }

        client.sendResponse(new HousekeepingActionResultComposer(DENIED_ACTION_KEY, false, 0, DENIED_MESSAGE));

        return false;
    }
}
