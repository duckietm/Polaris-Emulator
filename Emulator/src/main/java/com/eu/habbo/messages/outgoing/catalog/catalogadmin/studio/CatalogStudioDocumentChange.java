package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.List;

public record CatalogStudioDocumentChange(
        String entityType, String catalogType, int entityId, String operation, List<String> fields) {
    public CatalogStudioDocumentChange {
        fields = List.copyOf(fields);
    }
}
