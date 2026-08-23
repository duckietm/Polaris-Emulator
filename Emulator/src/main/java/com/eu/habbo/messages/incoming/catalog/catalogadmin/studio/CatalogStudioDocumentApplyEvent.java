package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeSetApplyResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;

public final class CatalogStudioDocumentApplyEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        this.packet.readInt(); // legacy version field
        this.packet.readInt(); // legacy revision field
        this.packet.readString(); // legacy lock token
        String format = this.packet.readString();
        String document = CatalogStudioRequestParser.parseDocument(this.packet);
        String fingerprint = this.packet.readString();
        String summary = this.packet.readString();
        CatalogChangeSetApplyResult result =
                studio().liveChangeSets().apply(operationId, actorId(), format, document, fingerprint, summary);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId,
                true,
                result.idempotentReplay() ? "ALREADY_APPLIED" : "APPLIED",
                result.idempotentReplay() ? "Operation was already applied" : "Live catalog updated",
                result.revision(),
                format,
                "",
                fingerprint,
                result.changedEntities()));
    }
}
