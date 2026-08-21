package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;
import java.util.Objects;

public record CatalogLiveReconciliationResult(
        CatalogVersionSnapshot snapshot, int importedChanges, List<CatalogMergeConflict> conflicts) {
    public CatalogLiveReconciliationResult {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (importedChanges < 0) throw new IllegalArgumentException("importedChanges cannot be negative");
        conflicts = List.copyOf(conflicts);
    }
}
