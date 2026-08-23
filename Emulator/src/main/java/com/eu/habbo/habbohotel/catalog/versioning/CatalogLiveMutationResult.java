package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogLiveMutationResult(long activeVersionId, long revision, CatalogChangeGroup historyGroup) {
    public CatalogLiveMutationResult {
        if (activeVersionId <= 0) throw new IllegalArgumentException("Active version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        historyGroup = Objects.requireNonNull(historyGroup, "historyGroup");
    }
}
