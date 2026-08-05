package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogPreviewOffer(
        CatalogOfferSnapshot offer,
        boolean eligible,
        List<String> reasons,
        List<CatalogPreviewProduct> products,
        boolean giftable) {
    public CatalogPreviewOffer(CatalogOfferSnapshot offer, boolean eligible, List<String> reasons) {
        this(offer, eligible, reasons, List.of(), false);
    }

    public CatalogPreviewOffer {
        reasons = List.copyOf(reasons);
        products = List.copyOf(products);
        eligible = reasons.isEmpty();
    }
}
