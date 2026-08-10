package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogSmartSaveResult(
        boolean replayed,
        String operationId,
        long draftVersionId,
        long revision,
        CatalogEntityType entityType,
        CatalogPageType catalogType,
        int entityId,
        CatalogChangeOperation operation,
        CatalogChangeGroup historyGroup,
        String entityJson,
        long serverDurationMs) {

    public CatalogSmartSaveResult {
        operationId = Objects.requireNonNull(operationId, "operationId");
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (entityId <= 0) throw new IllegalArgumentException("Entity ID must be positive");
        operation = Objects.requireNonNull(operation, "operation");
        historyGroup = Objects.requireNonNull(historyGroup, "historyGroup");
        if (serverDurationMs < 0) throw new IllegalArgumentException("Server duration cannot be negative");
    }

    public CatalogSmartSaveResult asReplay() {
        return replayed
                ? this
                : new CatalogSmartSaveResult(
                        true,
                        operationId,
                        draftVersionId,
                        revision,
                        entityType,
                        catalogType,
                        entityId,
                        operation,
                        historyGroup,
                        entityJson,
                        serverDurationMs);
    }
}
