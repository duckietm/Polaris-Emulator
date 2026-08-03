package com.eu.habbo.habbohotel.catalog.versioning;

public final class CatalogConcurrentModificationException extends RuntimeException {
    public CatalogConcurrentModificationException(long versionId, long expectedRevision) {
        super("Catalog draft " + versionId + " is no longer at revision " + expectedRevision);
    }
}
