package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record CatalogLockResult(boolean acquired, Optional<UUID> token, int ownerId, Instant expiresAt) {

    static CatalogLockResult acquired(CatalogLockRecord record) {
        return new CatalogLockResult(true, Optional.of(record.token()), record.ownerId(), record.expiresAt());
    }

    static CatalogLockResult denied(CatalogLockRecord record) {
        return new CatalogLockResult(false, Optional.empty(), record.ownerId(), record.expiresAt());
    }

    static CatalogLockResult missing() {
        return new CatalogLockResult(false, Optional.empty(), 0, Instant.EPOCH);
    }
}
