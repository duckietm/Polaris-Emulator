package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminPublishEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        this.client.sendResponse(new CatalogAdminResultComposer(
                false, "Legacy publish is disabled; validate and publish the shared Catalog Studio draft"));
    }
}
