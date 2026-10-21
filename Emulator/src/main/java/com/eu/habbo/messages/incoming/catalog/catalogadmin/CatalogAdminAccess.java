package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

/**
 * The gate every catalog admin handler starts with: the catalog tools need {@link Permission#ACC_CATALOGFURNI}. A
 * refused request is answered at once, so the client does not wait for its own timeout.
 */
final class CatalogAdminAccess {
    static final String DENIED_MESSAGE = "No permission";

    private CatalogAdminAccess() {}

    /** True when the user may use the catalog tools; otherwise they are told so and false is returned. */
    static boolean check(GameClient client) {
        if (client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            return true;
        }

        client.sendResponse(new CatalogAdminResultComposer(false, DENIED_MESSAGE));

        return false;
    }
}
