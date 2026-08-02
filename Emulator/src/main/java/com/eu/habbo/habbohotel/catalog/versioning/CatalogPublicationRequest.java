package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogPublicationRequest(
        long draftVersionId, long expectedRevision, int actorId, String nextDraftLabel) {

    public CatalogPublicationRequest {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        nextDraftLabel = Objects.requireNonNull(nextDraftLabel, "nextDraftLabel");
    }
}
