package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioPublishConflict(String catalogType, String entityType, int entityId, String field) {
    public CatalogStudioPublishConflict {
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        entityType = Objects.requireNonNull(entityType, "entityType");
        field = Objects.requireNonNull(field, "field");
    }
}
