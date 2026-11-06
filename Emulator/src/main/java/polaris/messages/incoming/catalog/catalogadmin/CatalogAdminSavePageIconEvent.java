package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogSnapshotPatch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminSavePageIconEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int pageId = this.packet.readInt();
        int iconId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (iconId < 0) iconId = 0;
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        int targetIconId = iconId;
        var result = CatalogStudioRuntime.services()
                .liveMutations()
                .updatePage(
                        envelope.expectedRevision(),
                        envelope.operationId(),
                        this.client.getUser().getUserInfo().getId(),
                        envelope.summary(),
                        pageType,
                        pageId,
                        page -> CatalogSnapshotPatch.setPageIcon(page, targetIconId),
                        CatalogChangeOperation.UPDATE);
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Page icon saved live at revision " + result.revision()));
    }
}
