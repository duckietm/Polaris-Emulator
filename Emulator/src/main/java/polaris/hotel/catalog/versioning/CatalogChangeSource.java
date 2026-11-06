package polaris.hotel.catalog.versioning;

public enum CatalogChangeSource {
    UI,
    SQL,
    LIVE_SYNC,
    RESTORE,
    UNDO
}
