package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogSnapshotPatch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminMoveOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int offerId = this.packet.readInt();
        int orderNumber = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        if (orderNumber < 0) orderNumber = 0;

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        int targetOrderNumber = orderNumber;
        var result = CatalogStudioRuntime.services()
                .liveMutations()
                .updateOffer(
                        envelope.expectedRevision(),
                        envelope.operationId(),
                        this.client.getUser().getUserInfo().getId(),
                        envelope.summary(),
                        pageType,
                        offerId,
                        offer -> CatalogSnapshotPatch.setOfferOrder(offer, targetOrderNumber),
                        CatalogChangeOperation.MOVE);
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Offer reordered live at revision " + result.revision()));
    }
}
