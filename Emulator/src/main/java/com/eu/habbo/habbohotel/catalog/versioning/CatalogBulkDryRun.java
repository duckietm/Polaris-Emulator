package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogBulkDryRun(
        long draftVersionId, long revision, List<CatalogChangeEntry> changes, String fingerprint) {
    public CatalogBulkDryRun {
        changes = List.copyOf(changes);
    }
}
