package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSnapshotPatch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CatalogAdminReorderOffersEvent extends MessageHandler {
    private static final int MAX_BATCH_SIZE = 1000;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int count = this.packet.readInt();
        if (count <= 0 || count > MAX_BATCH_SIZE) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid reorder batch size"));
            return;
        }

        List<OfferOrder> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int offerId = this.packet.readInt();
            int orderNumber = this.packet.readInt();
            orders.add(new OfferOrder(offerId, orderNumber));
        }

        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var mutations = CatalogStudioRuntime.services().mutations();
        var draft = mutations.loadDraft(envelope.draftVersionId(), envelope.expectedRevision());
        Set<Integer> ids = new HashSet<>();
        Integer pageId = null;
        List<CatalogDraftMutationRequest> requests = new ArrayList<>(orders.size());
        Gson gson = new Gson();
        for (OfferOrder order : orders) {
            if (order.offerId() <= 0 || order.orderNumber() < 0 || !ids.add(order.offerId())) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer reorder entry"));
                return;
            }
            var offer = draft.offer(pageType, order.offerId()).orElse(null);
            if (offer == null) {
                this.client.sendResponse(
                        new CatalogAdminResultComposer(false, "Offer not found in shared draft: " + order.offerId()));
                return;
            }
            if (pageId == null) pageId = offer.pageId();
            if (offer.pageId() != pageId) {
                this.client.sendResponse(
                        new CatalogAdminResultComposer(false, "Every reordered offer must belong to the same page"));
                return;
            }
            requests.add(new CatalogDraftMutationRequest(
                    envelope.draftVersionId(),
                    envelope.expectedRevision(),
                    this.client.getHabbo().getHabboInfo().getId(),
                    new CatalogLockKey(CatalogEntityType.PAGE, pageType, pageId),
                    envelope.lockToken(),
                    envelope.summary(),
                    CatalogEntityType.OFFER,
                    offer.offerId(),
                    CatalogChangeOperation.MOVE,
                    gson.toJson(CatalogSnapshotPatch.setOfferOrder(offer, order.orderNumber()))));
        }
        var result = mutations.applyBatch(requests);
        this.client.sendResponse(new CatalogAdminResultComposer(
                true, "Offers reordered in shared draft at revision " + result.revision()));
    }

    private record OfferOrder(int offerId, int orderNumber) {}
}
