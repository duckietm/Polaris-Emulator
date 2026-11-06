package polaris.hotel.catalog.versioning;

public final class CatalogUndoConflictException extends RuntimeException {
    CatalogUndoConflictException(String message) {
        super(message);
    }
}
