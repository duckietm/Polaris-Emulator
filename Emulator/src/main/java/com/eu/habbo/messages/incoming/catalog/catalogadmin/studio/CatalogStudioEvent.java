package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

abstract class CatalogStudioEvent extends MessageHandler {
    final boolean authorize() {
        if (this.client != null
                && this.client.getHabbo() != null
                && this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            return true;
        }
        if (this.client != null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
        }
        return false;
    }

    final int actorId() {
        return this.client.getHabbo().getHabboInfo().getId();
    }

    final CatalogStudioRuntime.Services studio() {
        return CatalogStudioRuntime.services();
    }
}
