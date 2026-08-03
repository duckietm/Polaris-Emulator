package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioChangedEntity(String entityType, int entityId) {
    public CatalogStudioChangedEntity {
        entityType = Objects.requireNonNull(entityType, "entityType");
    }
}
