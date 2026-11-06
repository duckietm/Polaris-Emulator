package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogSnapshotPatch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

public class CatalogAdminSavePageImagesEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!CatalogAdminAccess.check(this.client)) {
            return;
        }

        int pageId = this.packet.readInt();
        String headerImage = this.packet.readString();
        String teaserImage = this.packet.readString();
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
                        page -> CatalogSnapshotPatch.setPageImages(page, headerImage, teaserImage),
                        CatalogChangeOperation.UPDATE);
        this.client.sendResponse(
                new CatalogAdminResultComposer(true, "Page images saved live at revision " + result.revision()));
    }
}
