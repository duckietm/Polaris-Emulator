package polaris.messages.incoming.catalog.catalogadmin;

import com.google.gson.Gson;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogEntityType;
import polaris.hotel.catalog.versioning.CatalogOfferSnapshot;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminSaveOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int offerId = this.packet.readInt();
        int pageId = this.packet.readInt();
        String itemIds = this.packet.readString();
        String catalogName = this.packet.readString();
        int costCredits = this.packet.readInt();
        int costPoints = this.packet.readInt();
        int pointsType = this.packet.readInt();
        int amount = this.packet.readInt();
        int clubOnly = this.packet.readInt();
        String extradata = this.packet.readString();
        boolean haveOffer = this.packet.readBoolean();
        int offerIdGroup = this.packet.readInt();
        int limitedStack = this.packet.readInt();
        int orderNumber = this.packet.readInt();
        int songId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        CatalogAdminOfferPayload payload = CatalogAdminOfferPayload.validate(
                pageId,
                itemIds,
                catalogName,
                costCredits,
                costPoints,
                pointsType,
                amount,
                clubOnly,
                extradata,
                haveOffer,
                offerIdGroup,
                limitedStack,
                orderNumber,
                songId,
                pageType);
        if (payload == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer payload"));
            return;
        }

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        for (int itemId : payload.baseItemIds()) {
            if (Emulator.getGameEnvironment().getItemManager().getItem(itemId) == null) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Base item not found: " + itemId));
                return;
            }
        }

        var offerData = CatalogAdminOfferDraftData.from(payload);
        Gson gson = new Gson();
        String operationId = CatalogAdminSmartSaveResponder.operationId(envelope, "saveOffer");
        var liveMutations = CatalogStudioRuntime.services().liveMutations();
        try {
            var batch = liveMutations.applyBatch(
                    java.util.List.of(CatalogAdminLiveRequest.of(
                            envelope,
                            this.client.getUser().getUserInfo().getId(),
                            CatalogEntityType.OFFER,
                            pageType,
                            offerId,
                            CatalogChangeOperation.UPDATE,
                            gson.toJson(offerData))),
                    live -> {
                        if (live.page(pageType, payload.pageId).isEmpty()) {
                            throw new IllegalArgumentException("Live catalog page not found: " + payload.pageId);
                        }
                        CatalogOfferSnapshot existingItem =
                                live.offer(pageType, offerId).orElse(null);
                        if (existingItem == null) {
                            throw new IllegalArgumentException("Offer not found: " + offerId);
                        }
                        if (payload.limitedStack < existingItem.limitedStack()) {
                            throw new IllegalArgumentException("Limited stack cannot be reduced");
                        }
                    });
            var result = CatalogAdminLiveRequest.smartSaveResult(
                    operationId, batch, batch.changes().getFirst());
            this.client.sendResponse(CatalogAdminSmartSaveResponder.success(
                    "saveOffer",
                    "Offer saved live at revision " + result.revision(),
                    result,
                    this.client.getUser().getUserInfo().getUsername(),
                    gson));
        } catch (RuntimeException exception) {
            this.client.sendResponse(CatalogAdminSmartSaveResponder.failure(
                    operationId,
                    "saveOffer",
                    envelope.draftVersionId(),
                    envelope.expectedRevision(),
                    "OFFER",
                    pageType.name(),
                    offerId,
                    exception,
                    gson));
        }
    }
}
