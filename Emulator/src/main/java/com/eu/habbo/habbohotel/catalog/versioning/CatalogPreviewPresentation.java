package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogPreviewPresentation(List<CatalogPreviewProduct> products, boolean giftable) {
    public CatalogPreviewPresentation {
        products = List.copyOf(products);
    }

    public static CatalogPreviewPresentation empty() {
        return new CatalogPreviewPresentation(List.of(), false);
    }
}
