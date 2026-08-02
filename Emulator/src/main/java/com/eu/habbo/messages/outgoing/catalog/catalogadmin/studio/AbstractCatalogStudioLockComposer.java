package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.time.Instant;
import java.util.Objects;

abstract class AbstractCatalogStudioLockComposer extends MessageComposer {
    private final String operationId;
    private final boolean success;
    private final String code;
    private final String message;
    private final long draftVersionId;
    private final String entityType;
    private final String catalogType;
    private final int entityId;
    private final int ownerId;
    private final String ownerName;
    private final String token;
    private final Instant expiresAt;

    AbstractCatalogStudioLockComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long draftVersionId,
            String entityType,
            String catalogType,
            int entityId,
            int ownerId,
            String ownerName,
            String token,
            Instant expiresAt) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.success = success;
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.draftVersionId = draftVersionId;
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.catalogType = Objects.requireNonNull(catalogType, "catalogType");
        this.entityId = entityId;
        this.ownerId = ownerId;
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.token = Objects.requireNonNull(token, "token");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    final ServerMessage appendPayload() {
        this.response.appendString(operationId);
        this.response.appendBoolean(success);
        this.response.appendString(code);
        this.response.appendString(message);
        this.response.appendInt(Math.toIntExact(draftVersionId));
        this.response.appendString(entityType);
        this.response.appendString(catalogType);
        this.response.appendInt(entityId);
        this.response.appendInt(ownerId);
        this.response.appendString(ownerName);
        this.response.appendString(token);
        this.response.appendString(expiresAt.toString());
        return this.response;
    }
}
