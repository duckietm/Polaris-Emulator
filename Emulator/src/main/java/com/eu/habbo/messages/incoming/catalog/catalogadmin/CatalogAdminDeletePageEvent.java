package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminDeletePageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var result = CatalogStudioRuntime.services()
                .liveMutations()
                .apply(
                        CatalogAdminLiveRequest.of(
                                envelope,
                                this.client.getHabbo().getHabboInfo().getId(),
                                CatalogEntityType.PAGE,
                                pageType,
                                pageId,
                                CatalogChangeOperation.DELETE,
                                null),
                        live -> {
                            if (live.page(pageType, pageId).isEmpty()) {
                                throw new IllegalArgumentException("Live catalog page not found: " + pageId);
                            }
                            if (live.pages().stream()
                                    .anyMatch(page -> page.catalogType() == pageType && page.parentId() == pageId)) {
                                throw new IllegalArgumentException("Move or delete child pages first");
                            }
                            if (live.offers().stream()
                                    .anyMatch(offer -> offer.catalogType() == pageType && offer.pageId() == pageId)) {
                                throw new IllegalArgumentException("Move or delete offers on this page first");
                            }
                        });
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Page deleted live at revision " + result.revision()));
    }
}
