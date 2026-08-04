package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogDraftPreview(
        long draftVersionId, long revision, List<CatalogPageSnapshot> pages, List<CatalogPreviewOffer> offers) {
    public CatalogDraftPreview {
        pages = List.copyOf(pages);
        offers = List.copyOf(offers);
    }
}
