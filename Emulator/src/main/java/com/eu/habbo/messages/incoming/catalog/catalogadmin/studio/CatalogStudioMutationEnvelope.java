package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import java.util.Objects;
import java.util.UUID;

public record CatalogStudioMutationEnvelope(
        long draftVersionId, long expectedRevision, UUID lockToken, String summary) {

    public CatalogStudioMutationEnvelope {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        lockToken = Objects.requireNonNull(lockToken, "lockToken");
        summary = Objects.requireNonNull(summary, "summary");
    }
}
