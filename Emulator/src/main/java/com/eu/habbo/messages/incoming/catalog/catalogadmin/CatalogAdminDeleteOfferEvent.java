package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminDeleteOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var mutations = CatalogStudioRuntime.services().mutations();
        var draft = mutations.loadDraft(envelope.draftVersionId(), envelope.expectedRevision());
        if (draft.offer(pageType, offerId).isEmpty()) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Offer not found in shared draft: " + offerId));
            return;
        }
        int limitedSells = CatalogStudioRuntime.services()
                .operationalOffers()
                .findLimitedSells(offerId)
                .orElse(0);
        if (limitedSells > 0) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Limited offers with completed sales cannot be deleted"));
            return;
        }
        var result = mutations.apply(new CatalogDraftMutationRequest(
                envelope.draftVersionId(),
                envelope.expectedRevision(),
                this.client.getHabbo().getHabboInfo().getId(),
                new CatalogLockKey(CatalogEntityType.OFFER, pageType, offerId),
                envelope.lockToken(),
                envelope.summary(),
                CatalogEntityType.OFFER,
                offerId,
                CatalogChangeOperation.DELETE,
                null));
        this.client.sendResponse(new CatalogAdminResultComposer(
                true, "Offer deleted from shared draft at revision " + result.revision()));
    }
}
