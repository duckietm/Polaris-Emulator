package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeFieldDiff;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogImportDryRun;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentChange;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;

public final class CatalogStudioDocumentDryRunEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        this.packet.readInt(); // legacy version field
        this.packet.readInt(); // legacy revision field
        String format = this.packet.readString();
        String document = CatalogStudioRequestParser.parseDocument(this.packet);
        CatalogImportDryRun dryRun = studio().liveChangeSets().dryRun(format, document);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId,
                true,
                "DRY_RUN_READY",
                "Dry-run ready",
                dryRun.revision(),
                format,
                dryRun.normalizedDocument(),
                dryRun.fingerprint(),
                dryRun.changes().size(),
                dryRun.changes().stream()
                        .map(change -> new CatalogStudioDocumentChange(
                                change.entityType().name(),
                                change.catalogType().name(),
                                change.entityId(),
                                change.operation().name(),
                                CatalogChangeFieldDiff.fields(change)))
                        .toList()));
    }
}
