package com.eu.habbo.habbohotel.catalog.versioning;

public final class CatalogLockPersistenceException extends RuntimeException {
    CatalogLockPersistenceException(Throwable cause) {
        super("Catalog lock persistence failed", cause);
    }
}
