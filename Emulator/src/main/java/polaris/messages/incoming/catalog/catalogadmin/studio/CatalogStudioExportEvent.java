package polaris.messages.incoming.catalog.catalogadmin.studio;

import polaris.hotel.catalog.versioning.CatalogVersionSnapshot;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;

public final class CatalogStudioExportEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        long draftVersionId = this.packet.readInt();
        long expectedRevision = this.packet.readInt();
        String format = this.packet.readString();
        CatalogVersionSnapshot live = studio().queries().loadLiveSnapshot(draftVersionId);
        if (live.version().revision() != expectedRevision) {
            throw new IllegalArgumentException("Catalog export revision is stale");
        }
        String document = studio().documents().export(live, format);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId, true, "EXPORTED", "Live catalog exported", expectedRevision, format, document, "", 0));
    }
}
