package polaris.hotel.catalog.versioning;

import java.util.Objects;
import polaris.hotel.catalog.CatalogPageType;

public record CatalogMergeConflict(
        CatalogEntityType entityType, CatalogPageType catalogType, int entityId, String field) {
    public CatalogMergeConflict {
        entityType = Objects.requireNonNull(entityType, "entityType");
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        field = Objects.requireNonNull(field, "field");
    }
}
