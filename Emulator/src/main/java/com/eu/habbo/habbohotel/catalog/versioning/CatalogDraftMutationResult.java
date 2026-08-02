package com.eu.habbo.habbohotel.catalog.versioning;

public record CatalogDraftMutationResult(long revision, int entityId, CatalogChangeEntry change) {}
