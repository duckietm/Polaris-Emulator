package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;
import java.util.Objects;

public record CatalogPublicationResult(
        boolean published,
        boolean noChanges,
        CatalogValidationReport validation,
        long activeVersionId,
        long draftVersionId,
        long revision,
        int importedChanges,
        List<CatalogMergeConflict> conflicts) {

    public CatalogPublicationResult {
        validation = Objects.requireNonNull(validation, "validation");
        if (importedChanges < 0) throw new IllegalArgumentException("importedChanges cannot be negative");
        conflicts = List.copyOf(conflicts);
    }
}
