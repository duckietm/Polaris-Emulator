package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;

public class CatalogAdminCreateOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

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
        String operationId = CatalogAdminSmartSaveResponder.operationId(envelope, "createOffer");
        var liveMutations = CatalogStudioRuntime.services().liveMutations();
        try {
            var batch = liveMutations.applyBatch(
                    java.util.List.of(CatalogAdminLiveRequest.of(
                            envelope,
                            this.client.getHabbo().getHabboInfo().getId(),
                            CatalogEntityType.OFFER,
                            pageType,
                            0,
                            CatalogChangeOperation.CREATE,
                            gson.toJson(offerData))),
                    live -> {
                        if (live.page(pageType, payload.pageId).isEmpty()) {
                            throw new IllegalArgumentException("Live catalog page not found: " + payload.pageId);
                        }
                    });
            var result = CatalogAdminLiveRequest.smartSaveResult(
                    operationId, batch, batch.changes().getFirst());
            this.client.sendResponse(CatalogAdminSmartSaveResponder.success(
                    "createOffer",
                    "Offer created live: " + result.entityId() + " at revision " + result.revision(),
                    result,
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    gson));
        } catch (IllegalArgumentException
                | com.eu.habbo.habbohotel.catalog.versioning.CatalogConcurrentModificationException
                | com.eu.habbo.habbohotel.catalog.versioning.CatalogUndoConflictException exception) {
            this.client.sendResponse(CatalogAdminSmartSaveResponder.failure(
                    operationId,
                    "createOffer",
                    envelope.draftVersionId(),
                    envelope.expectedRevision(),
                    "OFFER",
                    pageType.name(),
                    0,
                    exception,
                    gson));
        }
    }
}
