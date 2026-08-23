package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentWireCodec;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class CatalogStudioDocumentResultComposer extends MessageComposer {
    private final String operationId;
    private final boolean success;
    private final String code;
    private final String message;
    private final long revision;
    private final String format;
    private final String document;
    private final String fingerprint;
    private final int changedEntities;
    private final java.util.List<CatalogStudioDocumentChange> changes;

    public CatalogStudioDocumentResultComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            String format,
            String document,
            String fingerprint,
            int changedEntities) {
        this(
                operationId,
                success,
                code,
                message,
                revision,
                format,
                document,
                fingerprint,
                changedEntities,
                java.util.List.of());
    }

    public CatalogStudioDocumentResultComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            String format,
            String document,
            String fingerprint,
            int changedEntities,
            java.util.List<CatalogStudioDocumentChange> changes) {
        this.operationId = operationId;
        this.success = success;
        this.code = code;
        this.message = message;
        this.revision = revision;
        this.format = format;
        this.document = document;
        this.fingerprint = fingerprint;
        this.changedEntities = changedEntities;
        this.changes = java.util.List.copyOf(changes);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioDocumentResultComposer);
        this.response.appendString(operationId);
        this.response.appendBoolean(success);
        this.response.appendString(code);
        this.response.appendString(message);
        this.response.appendInt(Math.toIntExact(revision));
        this.response.appendString(format);
        CatalogStudioDocumentWireCodec.EncodedDocument encoded = CatalogStudioDocumentWireCodec.encode(document);
        this.response.appendString(encoded.encoding());
        this.response.appendInt(encoded.chunks().size());
        encoded.chunks().forEach(this.response::appendString);
        this.response.appendString(fingerprint);
        this.response.appendInt(changedEntities);
        this.response.appendInt(changes.size());
        for (CatalogStudioDocumentChange change : changes) {
            this.response.appendString(change.entityType());
            this.response.appendString(change.catalogType());
            this.response.appendInt(change.entityId());
            this.response.appendString(change.operation());
            this.response.appendInt(change.fields().size());
            change.fields().forEach(this.response::appendString);
        }
        return this.response;
    }
}
