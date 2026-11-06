package polaris.hotel.catalog.versioning;

public record CatalogHistoryEntryState(CatalogEntityType entityType, int entityId, CatalogChangeOperation operation) {}
