package polaris.hotel.catalog.versioning;

public record CatalogChangeSetApplyResult(long revision, int changedEntities, boolean idempotentReplay) {}
