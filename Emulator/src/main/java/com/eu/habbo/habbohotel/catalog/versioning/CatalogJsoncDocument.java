package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogJsoncDocument(
        int schemaVersion,
        long baseVersionId,
        long baseRevision,
        List<CatalogPageSnapshot> pages,
        List<CatalogOfferSnapshot> offers) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CatalogJsoncDocument {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Catalog Studio JSONC schema: " + schemaVersion);
        }
        if (baseVersionId <= 0) throw new IllegalArgumentException("Base version ID must be positive");
        if (baseRevision < 0) throw new IllegalArgumentException("Base revision cannot be negative");
        pages = List.copyOf(pages);
        offers = List.copyOf(offers);
    }
}
