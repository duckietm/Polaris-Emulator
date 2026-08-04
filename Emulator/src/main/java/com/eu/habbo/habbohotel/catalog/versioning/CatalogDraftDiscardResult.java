package com.eu.habbo.habbohotel.catalog.versioning;

public record CatalogDraftDiscardResult(long activeVersionId, long draftVersionId, long revision) {}
