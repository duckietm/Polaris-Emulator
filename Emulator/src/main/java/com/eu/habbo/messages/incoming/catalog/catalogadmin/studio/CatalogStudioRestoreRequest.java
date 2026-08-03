package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioRestoreRequest(
        String operationId, long draftVersionId, long expectedRevision, long sourceVersionId) {
    public CatalogStudioRestoreRequest {
        operationId = Objects.requireNonNull(operationId, "operationId");
    }
}
