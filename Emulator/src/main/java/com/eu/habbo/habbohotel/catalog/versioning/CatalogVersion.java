package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.Objects;

public record CatalogVersion(
        long id,
        CatalogVersionStatus status,
        Long basedOnVersionId,
        long revision,
        String label,
        int createdBy,
        Instant createdAt,
        Integer publishedBy,
        Instant publishedAt) {

    public CatalogVersion {
        if (id <= 0) throw new IllegalArgumentException("Version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        status = Objects.requireNonNull(status, "status");
        label = Objects.requireNonNull(label, "label");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
