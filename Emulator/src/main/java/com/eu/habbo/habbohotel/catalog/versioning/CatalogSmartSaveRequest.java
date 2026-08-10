package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;
import java.util.UUID;

public record CatalogSmartSaveRequest(
        String operationId,
        long draftVersionId,
        long expectedRevision,
        int actorId,
        String actorName,
        CatalogLockKey lockKey,
        UUID lockToken,
        String summary,
        CatalogEntityType entityType,
        int entityId,
        CatalogChangeOperation operation,
        String afterJson) {

    public CatalogSmartSaveRequest {
        CatalogOperationRecord.validateIdentity(operationId, actorId);
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        actorName = Objects.requireNonNull(actorName, "actorName");
        lockKey = Objects.requireNonNull(lockKey, "lockKey");
        lockToken = Objects.requireNonNull(lockToken, "lockToken");
        summary = Objects.requireNonNull(summary, "summary");
        entityType = Objects.requireNonNull(entityType, "entityType");
        operation = Objects.requireNonNull(operation, "operation");
        if (lockKey.entityType() != entityType) {
            throw new IllegalArgumentException("Lock entity type does not match the request");
        }
        if (operation == CatalogChangeOperation.CREATE) {
            if (entityId != 0) throw new IllegalArgumentException("Create requests must use entity ID 0");
        } else if (entityId <= 0) {
            throw new IllegalArgumentException("Entity ID must be positive");
        }
        if (operation != CatalogChangeOperation.DELETE && (afterJson == null || afterJson.isBlank())) {
            throw new IllegalArgumentException("The committed entity payload is required");
        }
    }
}
