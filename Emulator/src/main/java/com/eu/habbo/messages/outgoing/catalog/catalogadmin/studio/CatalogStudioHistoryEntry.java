package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioHistoryEntry(String entityType, int entityId, String operation) {
    public CatalogStudioHistoryEntry {
        entityType = Objects.requireNonNull(entityType, "entityType");
        operation = Objects.requireNonNull(operation, "operation");
    }
}
