package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;
import java.util.Objects;

public record CatalogSnapshotMergeResult(
        CatalogVersionSnapshot snapshot,
        List<CatalogMergeConflict> conflicts,
        List<CatalogChangeEntry> importedChanges) {
    public CatalogSnapshotMergeResult {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        conflicts = List.copyOf(conflicts);
        importedChanges = List.copyOf(importedChanges);
    }
}
