package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogValidationIssue(
        String code, CatalogEntityType entityType, int entityId, String field, String message) {

    public CatalogValidationIssue {
        code = Objects.requireNonNull(code, "code");
        entityType = Objects.requireNonNull(entityType, "entityType");
        field = Objects.requireNonNull(field, "field");
        message = Objects.requireNonNull(message, "message");
    }
}
