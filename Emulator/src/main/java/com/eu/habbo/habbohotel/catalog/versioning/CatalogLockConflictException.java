package com.eu.habbo.habbohotel.catalog.versioning;

public final class CatalogLockConflictException extends RuntimeException {
    CatalogLockConflictException(CatalogLockKey key) {
        super("The catalog lock is missing, expired, or owned by another administrator: "
                + key.entityType()
                + " #"
                + key.entityId());
    }
}
