package com.eu.habbo.habbohotel.catalog.versioning;

public record CatalogChangeSetApplyResult(long revision, int changedEntities, boolean idempotentReplay) {}
