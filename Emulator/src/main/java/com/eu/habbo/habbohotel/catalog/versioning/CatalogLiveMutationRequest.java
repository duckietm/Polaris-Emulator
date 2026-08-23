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
        String afterJson) {

    public CatalogLiveMutationRequest {
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        summary = Objects.requireNonNull(summary, "summary");
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        operation = Objects.requireNonNull(operation, "operation");
        if (catalogType == CatalogPageType.BOTH) throw new IllegalArgumentException("Catalog type must be concrete");
        if (entityId <= 0) throw new IllegalArgumentException("Entity ID must be positive");
        if (operation == CatalogChangeOperation.CREATE) {
            throw new IllegalArgumentException("Live creates require server-side identity allocation");
        }
        if (operation != CatalogChangeOperation.DELETE && (afterJson == null || afterJson.isBlank())) {
            throw new IllegalArgumentException("The committed entity payload is required");
        }
    }
}
