package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogImportDryRun;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;

public final class CatalogStudioDocumentDryRunEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        long draftVersionId = this.packet.readInt();
        long expectedRevision = this.packet.readInt();
        String format = this.packet.readString();
        String document = this.packet.readString();
        CatalogImportDryRun dryRun = studio().changeSets().dryRun(draftVersionId, expectedRevision, format, document);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId,
                true,
                "DRY_RUN_READY",
                "Dry-run ready",
                dryRun.revision(),
                format,
                dryRun.normalizedDocument(),
                dryRun.fingerprint(),
                dryRun.changes().size()));
    }
}
