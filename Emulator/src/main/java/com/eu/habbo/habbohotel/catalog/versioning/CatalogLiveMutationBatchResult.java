package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;
import java.util.Objects;

public record CatalogLiveMutationBatchResult(
        long activeVersionId, long revision, CatalogChangeGroup historyGroup, List<CatalogChangeEntry> changes) {
    public CatalogLiveMutationBatchResult {
        if (activeVersionId <= 0) throw new IllegalArgumentException("Active version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        historyGroup = Objects.requireNonNull(historyGroup, "historyGroup");
        changes = List.copyOf(changes);
        if (changes.isEmpty()) throw new IllegalArgumentException("A live mutation batch cannot be empty");
    }
}
