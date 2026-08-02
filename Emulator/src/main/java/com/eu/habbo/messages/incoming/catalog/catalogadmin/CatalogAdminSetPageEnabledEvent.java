package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogAdminMutationService;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminSetPageEnabledEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        boolean enabled = this.packet.readBoolean();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        var result = CatalogAdminMutationService.setPageEnabled(pageId, enabled, pageType);
        this.client.sendResponse(new CatalogAdminResultComposer(result.success(), result.message()));
    }
}
