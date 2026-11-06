package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminPageDetailsComposer;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminLoadPageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        this.packet.readInt(); // legacy version field
        this.packet.readInt(); // legacy revision field
        var page = CatalogStudioRuntime.services()
                .liveMutations()
                .loadLiveForRead()
                .page(pageType, pageId)
                .orElse(null);
        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Live catalog page not found: " + pageId));
            return;
        }

        this.client.sendResponse(new CatalogAdminPageDetailsComposer(page));
    }
}
