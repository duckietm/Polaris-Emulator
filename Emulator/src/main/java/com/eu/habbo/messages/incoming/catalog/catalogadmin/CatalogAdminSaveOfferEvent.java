package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogOfferSnapshot;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;

public class CatalogAdminSaveOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
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
        var mutations = CatalogStudioRuntime.services().mutations();
        var draft = mutations.loadDraft(envelope.draftVersionId(), envelope.expectedRevision());
        if (draft.page(pageType, payload.pageId).isEmpty()) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Page not found in shared draft: " + payload.pageId));
            return;
        }

        for (int itemId : payload.baseItemIds()) {
            if (Emulator.getGameEnvironment().getItemManager().getItem(itemId) == null) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Base item not found: " + itemId));
                return;
            }
        }

        CatalogOfferSnapshot existingItem = draft.offer(pageType, offerId).orElse(null);
        if (existingItem == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
            return;
        }
        if (payload.limitedStack < existingItem.limitedStack()) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Limited stack cannot be reduced"));
            return;
        }

        CatalogOfferSnapshot edited = new CatalogOfferSnapshot(
                pageType,
                offerId,
                itemIds == null || itemIds.isBlank() ? existingItem.itemIds() : payload.itemIds,
                payload.pageId,
                payload.catalogName,
                pageType == CatalogPageType.BUILDER ? 0 : payload.costCredits,
                pageType == CatalogPageType.BUILDER ? 0 : payload.costPoints,
                pageType == CatalogPageType.BUILDER ? 0 : payload.pointsType,
                pageType == CatalogPageType.BUILDER ? 1 : payload.amount,
                pageType == CatalogPageType.BUILDER ? 0 : payload.limitedStack,
                payload.orderNumber,
                pageType == CatalogPageType.BUILDER ? -1 : payload.offerIdGroup,
                pageType == CatalogPageType.BUILDER ? 0 : payload.songId,
                payload.extradata,
                pageType == CatalogPageType.BUILDER || payload.haveOffer,
                pageType != CatalogPageType.BUILDER && payload.clubOnly == 1);
        var result = mutations.apply(new CatalogDraftMutationRequest(
                envelope.draftVersionId(),
                envelope.expectedRevision(),
                this.client.getHabbo().getHabboInfo().getId(),
                new CatalogLockKey(CatalogEntityType.OFFER, pageType, offerId),
                envelope.lockToken(),
                envelope.summary(),
                CatalogEntityType.OFFER,
                offerId,
                CatalogChangeOperation.UPDATE,
                new Gson().toJson(edited)));
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Offer saved in shared draft at revision " + result.revision()));
    }
}
