package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CatalogLockRecord(long versionId, CatalogLockKey key, int ownerId, UUID token, Instant expiresAt) {
    public CatalogLockRecord {
        if (versionId <= 0) throw new IllegalArgumentException("Version ID must be positive");
        if (ownerId <= 0) throw new IllegalArgumentException("Owner ID must be positive");
        key = Objects.requireNonNull(key, "key");
        token = Objects.requireNonNull(token, "token");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
