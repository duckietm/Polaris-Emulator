package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CatalogHistoryGroupState(
        long id,
        long revision,
        int actorId,
        String actorName,
        String summary,
        CatalogChangeSource source,
        Instant createdAt,
        List<CatalogHistoryEntryState> entries) {

    public CatalogHistoryGroupState {
        actorName = Objects.requireNonNull(actorName, "actorName");
        summary = Objects.requireNonNull(summary, "summary");
        source = Objects.requireNonNull(source, "source");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        entries = List.copyOf(entries);
    }
}
