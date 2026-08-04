package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.List;
import java.util.Objects;

abstract class AbstractCatalogStudioOperationComposer extends MessageComposer {
    private final String operationId;
    private final boolean success;
    private final String code;
    private final String message;
    private final long revision;
    private final List<CatalogStudioChangedEntity> changedEntities;

    AbstractCatalogStudioOperationComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.success = success;
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.revision = revision;
        this.changedEntities = List.copyOf(changedEntities);
    }

    final ServerMessage appendPayload() {
        this.response.appendString(operationId);
        this.response.appendBoolean(success);
        this.response.appendString(code);
        this.response.appendString(message);
        this.response.appendInt(Math.toIntExact(revision));
        this.response.appendInt(changedEntities.size());
        for (CatalogStudioChangedEntity entity : changedEntities) {
            this.response.appendString(entity.entityType());
            this.response.appendInt(entity.entityId());
        }
        return this.response;
    }
}
