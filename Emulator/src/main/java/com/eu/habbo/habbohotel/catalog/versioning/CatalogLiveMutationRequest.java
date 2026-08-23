package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogLiveMutationRequest(
        long expectedRevision,
        int actorId,
        String summary,
        CatalogEntityType entityType,
        CatalogPageType catalogType,
        int entityId,
        CatalogChangeOperation operation,
        String afterJson,
        String operationId) {

    public CatalogLiveMutationRequest(
            long expectedRevision,
            int actorId,
            String summary,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            CatalogChangeOperation operation,
            String afterJson) {
        this(expectedRevision, actorId, summary, entityType, catalogType, entityId, operation, afterJson, "");
    }

    public CatalogLiveMutationRequest {
        if (expectedRevision < -1) throw new IllegalArgumentException("Expected revision cannot be less than -1");
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        summary = Objects.requireNonNull(summary, "summary");
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        operation = Objects.requireNonNull(operation, "operation");
        operationId = Objects.requireNonNull(operationId, "operationId");
        if (!operationId.isEmpty() && (operationId.isBlank() || operationId.length() > 96)) {
            throw new IllegalArgumentException("Operation ID must be nonblank and at most 96 characters");
        }
        if (catalogType == CatalogPageType.BOTH) throw new IllegalArgumentException("Catalog type must be concrete");
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
