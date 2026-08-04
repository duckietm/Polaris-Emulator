package com.eu.habbo.habbohotel.catalog.versioning;

public final class CatalogUndoConflictException extends RuntimeException {
    CatalogUndoConflictException(String message) {
        super(message);
    }
}
