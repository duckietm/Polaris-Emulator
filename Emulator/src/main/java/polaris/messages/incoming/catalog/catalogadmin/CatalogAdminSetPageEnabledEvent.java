package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogSnapshotPatch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminSetPageEnabledEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int pageId = this.packet.readInt();
        boolean enabled = this.packet.readBoolean();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        var result = CatalogStudioRuntime.services()
                .liveMutations()
                .updatePage(
                        envelope.expectedRevision(),
                        envelope.operationId(),
                        this.client.getUser().getUserInfo().getId(),
                        envelope.summary(),
                        pageType,
                        pageId,
                        page -> CatalogSnapshotPatch.setPageEnabled(page, enabled),
                        CatalogChangeOperation.UPDATE);
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Page enabled state saved live at revision " + result.revision()));
    }
}
