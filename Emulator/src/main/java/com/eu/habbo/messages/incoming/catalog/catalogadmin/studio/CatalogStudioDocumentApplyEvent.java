package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeSetApplyResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;
import java.util.UUID;

public final class CatalogStudioDocumentApplyEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        String operationId = this.packet.readString();
        long draftVersionId = this.packet.readInt();
        long expectedRevision = this.packet.readInt();
        UUID rootLockToken = UUID.fromString(this.packet.readString());
        String format = this.packet.readString();
        String document = CatalogStudioRequestParser.parseDocument(this.packet);
        String fingerprint = this.packet.readString();
        String summary = this.packet.readString();
        CatalogChangeSetApplyResult result = studio().changeSets()
                .apply(
                        operationId,
                        draftVersionId,
                        expectedRevision,
                        actorId(),
                        rootLockToken,
                        format,
                        document,
                        fingerprint,
                        summary);
        this.client.sendResponse(new CatalogStudioDocumentResultComposer(
                operationId,
                true,
                result.idempotentReplay() ? "ALREADY_APPLIED" : "APPLIED",
                result.idempotentReplay() ? "Operation was already applied" : "Draft updated",
                result.revision(),
                format,
                "",
                fingerprint,
                result.changedEntities()));
    }
}
