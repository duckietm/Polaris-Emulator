package polaris.hotel.catalog.versioning;

public record CatalogDraftDiscardResult(long activeVersionId, long draftVersionId, long revision) {}
