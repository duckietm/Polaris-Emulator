package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CatalogBulkRequest(
        CatalogBulkOperation operation,
        CatalogPageType catalogType,
        List<Integer> entityIds,
        int intValue,
        boolean booleanValue) {
    public CatalogBulkRequest {
        operation = Objects.requireNonNull(operation, "operation");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (catalogType == CatalogPageType.BOTH) throw new IllegalArgumentException("Bulk edits require one catalog");
        entityIds = List.copyOf(entityIds);
        if (entityIds.isEmpty() || entityIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Bulk edits require positive entity IDs");
        }
        if (new HashSet<>(entityIds).size() != entityIds.size()) {
            throw new IllegalArgumentException("Bulk entity IDs must be unique");
        }
    }
}
