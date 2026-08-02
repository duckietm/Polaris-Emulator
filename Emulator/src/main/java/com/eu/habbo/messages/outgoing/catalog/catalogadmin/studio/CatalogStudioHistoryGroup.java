package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CatalogStudioHistoryGroup(
        long id,
        long revision,
        int actorId,
        String actorName,
        String summary,
        String source,
        Instant createdAt,
        List<CatalogStudioHistoryEntry> entries) {

    public CatalogStudioHistoryGroup {
        actorName = Objects.requireNonNull(actorName, "actorName");
        summary = Objects.requireNonNull(summary, "summary");
        source = Objects.requireNonNull(source, "source");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        entries = List.copyOf(entries);
    }
}
