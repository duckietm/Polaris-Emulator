package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import java.util.Objects;
import java.util.UUID;

public record CatalogStudioMutationEnvelope(
        long draftVersionId, long expectedRevision, UUID lockToken, String summary, String operationId) {

    public CatalogStudioMutationEnvelope {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        lockToken = Objects.requireNonNull(lockToken, "lockToken");
        summary = Objects.requireNonNull(summary, "summary");
        operationId = Objects.requireNonNull(operationId, "operationId");
        if (!operationId.isEmpty() && (operationId.isBlank() || operationId.length() > 96)) {
            throw new IllegalArgumentException("Operation ID must be nonblank and at most 96 characters");
        }
    }
}
