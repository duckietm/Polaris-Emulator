package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogImportDryRun(
        CatalogChangeSource source,
        long draftVersionId,
        long revision,
        String normalizedDocument,
        List<CatalogChangeEntry> changes,
        String fingerprint) {
    public CatalogImportDryRun {
        changes = List.copyOf(changes);
    }
}
