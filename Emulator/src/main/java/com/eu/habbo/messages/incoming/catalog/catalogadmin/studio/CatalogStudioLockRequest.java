package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import java.util.Objects;
import java.util.UUID;

public record CatalogStudioLockRequest(String operationId, long draftVersionId, CatalogLockKey key, UUID token) {
    public CatalogStudioLockRequest {
        operationId = Objects.requireNonNull(operationId, "operationId");
        key = Objects.requireNonNull(key, "key");
    }
}
