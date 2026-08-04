package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CatalogStudioSessionState(
        long activeVersionId,
        long draftVersionId,
        long revision,
        Instant activeUpdatedAt,
        Instant draftCreatedAt,
        int pendingCount,
        List<CatalogStudioActorState> actors,
        boolean validationCurrent,
        int validationIssueCount,
        List<CatalogPublishedVersionState> publishedVersions) {

    public CatalogStudioSessionState {
        activeUpdatedAt = Objects.requireNonNull(activeUpdatedAt, "activeUpdatedAt");
        draftCreatedAt = Objects.requireNonNull(draftCreatedAt, "draftCreatedAt");
        actors = List.copyOf(actors);
        publishedVersions = List.copyOf(publishedVersions);
    }
}
