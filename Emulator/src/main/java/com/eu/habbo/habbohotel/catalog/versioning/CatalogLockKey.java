package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogLockKey(CatalogEntityType entityType, CatalogPageType catalogType, int entityId) {
    public CatalogLockKey {
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("A lock cannot target BOTH catalogs");
        }
        if (entityId <= 0) throw new IllegalArgumentException("Entity ID must be positive");
    }

    public CatalogLockKey(CatalogEntityType entityType, int entityId) {
        this(entityType, CatalogPageType.NORMAL, entityId);
    }
}
