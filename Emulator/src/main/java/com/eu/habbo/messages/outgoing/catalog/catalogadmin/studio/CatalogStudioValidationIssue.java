package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioValidationIssue(String code, String entityType, int entityId, String field, String message) {

    public CatalogStudioValidationIssue {
        code = Objects.requireNonNull(code, "code");
        entityType = Objects.requireNonNull(entityType, "entityType");
        field = Objects.requireNonNull(field, "field");
        message = Objects.requireNonNull(message, "message");
    }
}
