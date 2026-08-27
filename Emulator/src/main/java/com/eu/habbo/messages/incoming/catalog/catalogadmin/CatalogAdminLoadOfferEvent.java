package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminOfferDetailsComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminLoadOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        this.packet.readInt(); // legacy version field
        this.packet.readInt(); // legacy revision field
        var offer = CatalogStudioRuntime.services()
                .liveMutations()
                .loadLiveForRead()
                .offer(pageType, offerId)
                .orElse(null);
        if (offer == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Live catalog offer not found: " + offerId));
            return;
        }
        int limitedSells = pageType == CatalogPageType.NORMAL
                ? CatalogStudioRuntime.services()
                        .operationalOffers()
                        .findLimitedSells(offerId)
                        .orElse(0)
                : 0;
        this.client.sendResponse(new CatalogAdminOfferDetailsComposer(offer, limitedSells));
    }
}
