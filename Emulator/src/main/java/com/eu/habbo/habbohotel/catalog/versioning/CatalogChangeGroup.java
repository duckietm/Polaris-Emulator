package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CatalogChangeGroup(
        long id,
        long versionId,
        long revision,
        int actorId,
        String summary,
        CatalogChangeSource source,
        Instant createdAt,
        List<CatalogChangeEntry> entries) {

    public CatalogChangeGroup {
        if (id <= 0) throw new IllegalArgumentException("Change group ID must be positive");
        if (versionId <= 0) throw new IllegalArgumentException("Version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        if (actorId < 0) throw new IllegalArgumentException("Actor ID cannot be negative");
        summary = Objects.requireNonNull(summary, "summary");
        source = Objects.requireNonNull(source, "source");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        entries = List.copyOf(entries);
    }
}
