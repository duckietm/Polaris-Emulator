package polaris.hotel.catalog.versioning;

public record CatalogDraftMutationResult(long revision, int entityId, CatalogChangeEntry change) {}
