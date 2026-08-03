package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;

public final class CatalogStudioExportEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        long draftVersionId = this.packet.readInt();
        long expectedRevision = this.packet.readInt();
        String format = this.packet.readString();
        CatalogVersionSnapshot draft = studio().queries().loadDraftSnapshot(draftVersionId);
        if (draft.version().revision() != expectedRevision) {
            throw new IllegalArgumentException("Catalog export revision is stale");
        }
        String document = studio().documents().export(draft, format);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId, true, "EXPORTED", "Draft exported", expectedRevision, format, document, "", 0));
    }
}
