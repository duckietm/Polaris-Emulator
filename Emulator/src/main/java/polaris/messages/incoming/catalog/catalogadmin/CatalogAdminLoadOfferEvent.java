package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminOfferDetailsComposer;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminLoadOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
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
