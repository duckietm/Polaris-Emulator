package com.eu.habbo.habbohotel.catalog.versioning;

@FunctionalInterface
public interface CatalogPreviewPresentationResolver {
    CatalogPreviewPresentation resolve(CatalogOfferSnapshot offer);
}
