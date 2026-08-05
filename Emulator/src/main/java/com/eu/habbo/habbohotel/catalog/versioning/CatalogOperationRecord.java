package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogOperationRecord(
        String operationId,
        int actorId,
        long versionId,
        CatalogChangeSource source,
        long resultRevision,
        String requestFingerprint,
        CatalogChangeOperation action,
        CatalogEntityType entityType,
        CatalogPageType catalogType,
        Integer entityId,
        Long historyGroupId,
        String resultJson) {

    public CatalogOperationRecord {
        validateIdentity(operationId, actorId);
        if (versionId <= 0) throw new IllegalArgumentException("Version ID must be positive");
        source = Objects.requireNonNull(source, "source");
        if (resultRevision < 0) throw new IllegalArgumentException("Result revision cannot be negative");
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("An operation cannot target both catalogs");
        }
        if (entityId != null && entityId <= 0) throw new IllegalArgumentException("Entity ID must be positive");
        if (historyGroupId != null && historyGroupId <= 0) {
            throw new IllegalArgumentException("History group ID must be positive");
        }
    }

    static void validateIdentity(String operationId, int actorId) {
        operationId = Objects.requireNonNull(operationId, "operationId");
        if (operationId.isBlank() || operationId.length() > 96) {
            throw new IllegalArgumentException("Operation ID must be nonblank and at most 96 characters");
        }
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
    }
}
