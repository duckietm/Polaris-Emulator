package com.eu.habbo.habbohotel.catalog.versioning;

public record CatalogHistoryEntryState(CatalogEntityType entityType, int entityId, CatalogChangeOperation operation) {}
