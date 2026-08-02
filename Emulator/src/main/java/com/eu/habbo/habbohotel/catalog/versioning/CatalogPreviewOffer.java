package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogPreviewOffer(CatalogOfferSnapshot offer, boolean eligible, List<String> reasons) {
    public CatalogPreviewOffer {
        reasons = List.copyOf(reasons);
        eligible = reasons.isEmpty();
    }
}
