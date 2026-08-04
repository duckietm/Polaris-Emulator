package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.Objects;

public record CatalogRuntimeState(long activeVersionId, long draftVersionId, Instant updatedAt) {

    public CatalogRuntimeState {
        if (activeVersionId <= 0) throw new IllegalArgumentException("Active version ID must be positive");
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (activeVersionId == draftVersionId) {
            throw new IllegalArgumentException("Active and draft versions must differ");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
