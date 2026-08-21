package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogMergeConflict(
        CatalogEntityType entityType, CatalogPageType catalogType, int entityId, String field) {
    public CatalogMergeConflict {
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        field = Objects.requireNonNull(field, "field");
    }
}
