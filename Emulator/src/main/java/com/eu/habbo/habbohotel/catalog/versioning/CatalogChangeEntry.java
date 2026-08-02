package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogChangeEntry(
        long id,
        CatalogEntityType entityType,
        CatalogPageType catalogType,
        int entityId,
        CatalogChangeOperation operation,
        String beforeJson,
        String afterJson) {

    public CatalogChangeEntry {
        if (id < 0) throw new IllegalArgumentException("Change entry ID cannot be negative");
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("A change entry cannot target BOTH catalogs");
        }
        if (entityId <= 0) throw new IllegalArgumentException("Entity ID must be positive");
        operation = Objects.requireNonNull(operation, "operation");
        if (operation == CatalogChangeOperation.CREATE && afterJson == null) {
            throw new IllegalArgumentException("Create entries require after JSON");
        }
        if (operation == CatalogChangeOperation.DELETE && beforeJson == null) {
            throw new IllegalArgumentException("Delete entries require before JSON");
        }
        if ((operation == CatalogChangeOperation.UPDATE || operation == CatalogChangeOperation.MOVE)
                && (beforeJson == null || afterJson == null)) {
            throw new IllegalArgumentException("Update and move entries require before and after JSON");
        }
    }

    public CatalogChangeEntry(
            long id,
            CatalogEntityType entityType,
            int entityId,
            CatalogChangeOperation operation,
            String beforeJson,
            String afterJson) {
        this(id, entityType, CatalogPageType.NORMAL, entityId, operation, beforeJson, afterJson);
    }
}
