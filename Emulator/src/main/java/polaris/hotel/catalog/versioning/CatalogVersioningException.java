package polaris.hotel.catalog.versioning;

public final class CatalogVersioningException extends RuntimeException {
    CatalogVersioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
