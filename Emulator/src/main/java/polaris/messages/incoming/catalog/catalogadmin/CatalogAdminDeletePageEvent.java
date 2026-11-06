package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogEntityType;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminDeletePageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
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
                                this.client.getUser().getUserInfo().getId(),
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
