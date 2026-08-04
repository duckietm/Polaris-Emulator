package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

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
        this.operationId = operationId;
        this.success = success;
        this.code = code;
        this.message = message;
        this.revision = revision;
        this.format = format;
        this.document = document;
        this.fingerprint = fingerprint;
        this.changedEntities = changedEntities;
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
        this.response.appendString(document);
        this.response.appendString(fingerprint);
        this.response.appendInt(changedEntities);
        return this.response;
    }
}
