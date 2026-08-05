package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogPreviewProduct(
        String productType,
        int productClassId,
        String extraParam,
        int productCount,
        boolean uniqueLimitedItem,
        int uniqueLimitedSeriesSize,
        int uniqueLimitedItemsLeft) {
    public CatalogPreviewProduct {
        productType = Objects.requireNonNull(productType, "productType");
        extraParam = Objects.requireNonNullElse(extraParam, "");
        if (productCount <= 0) throw new IllegalArgumentException("Product count must be positive");
        if (uniqueLimitedSeriesSize < 0 || uniqueLimitedItemsLeft < 0) {
            throw new IllegalArgumentException("Limited product counts cannot be negative");
        }
    }
}
