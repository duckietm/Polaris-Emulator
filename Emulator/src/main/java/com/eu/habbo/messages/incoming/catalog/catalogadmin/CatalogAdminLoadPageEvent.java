package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminPageDetailsComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminLoadPageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, pageType);
        if (page == null) return;

        this.client.sendResponse(new CatalogAdminPageDetailsComposer(page));
    }
}
