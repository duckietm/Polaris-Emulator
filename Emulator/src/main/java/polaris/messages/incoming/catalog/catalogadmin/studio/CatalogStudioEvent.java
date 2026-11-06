package polaris.messages.incoming.catalog.catalogadmin.studio;

import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

abstract class CatalogStudioEvent extends MessageHandler {
    final boolean authorize() {
        if (this.client != null
                && this.client.getUser() != null
                && this.client.getUser().hasPermission(Permission.ACC_CATALOGFURNI)) {
            return true;
        }
        if (this.client != null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
        }
        return false;
    }

    final int actorId() {
        return this.client.getUser().getUserInfo().getId();
    }

    final CatalogStudioRuntime.Services studio() {
        return CatalogStudioRuntime.services();
    }
}
