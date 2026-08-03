package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogPublicationResult(
        boolean published, CatalogValidationReport validation, long activeVersionId, long draftVersionId) {

    public CatalogPublicationResult {
        validation = Objects.requireNonNull(validation, "validation");
    }
}
