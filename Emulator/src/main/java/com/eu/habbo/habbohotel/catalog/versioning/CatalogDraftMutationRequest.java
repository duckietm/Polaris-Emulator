package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;
import java.util.UUID;

public record CatalogDraftMutationRequest(
        long draftVersionId,
        long expectedRevision,
        int actorId,
        CatalogLockKey lockKey,
        UUID lockToken,
        String summary,
        CatalogEntityType entityType,
        int entityId,
        CatalogChangeOperation operation,
        String afterJson) {

    public CatalogDraftMutationRequest {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        lockKey = Objects.requireNonNull(lockKey, "lockKey");
        lockToken = Objects.requireNonNull(lockToken, "lockToken");
        summary = Objects.requireNonNull(summary, "summary");
        entityType = Objects.requireNonNull(entityType, "entityType");
        operation = Objects.requireNonNull(operation, "operation");
        if (operation == CatalogChangeOperation.CREATE) {
            if (entityId > 0) throw new IllegalArgumentException("Create requests must not provide an entity ID");
            if (afterJson == null) throw new IllegalArgumentException("Create requests require payload JSON");
        } else {
            if (entityId <= 0) throw new IllegalArgumentException("Existing entity ID must be positive");
            if (operation == CatalogChangeOperation.DELETE && afterJson != null) {
                throw new IllegalArgumentException("Delete requests cannot provide after JSON");
            }
            if (operation != CatalogChangeOperation.DELETE && afterJson == null) {
                throw new IllegalArgumentException("Update and move requests require after JSON");
            }
        }
    }

    public CatalogPageType catalogType() {
        return lockKey.catalogType();
    }
}
