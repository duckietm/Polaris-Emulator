package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogOfferSnapshot(
    CatalogPageType catalogType,
    int offerId,
    String itemIds,
    int pageId,
    String catalogName,
    int costCredits,
    int costPoints,
    int pointsType,
    int amount,
    int limitedStack,
    int orderNumber,
    int offerIdClient,
    int songId,
    String extradata,
    boolean haveOffer,
    boolean clubOnly) {

    public CatalogOfferSnapshot {
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("A physical catalog offer cannot belong to BOTH catalogs");
        }

        if (offerId <= 0) throw new IllegalArgumentException("Offer ID must be positive");
    }

    public CatalogOfferSnapshot(
        int offerId,
        String itemIds,
        int pageId,
        String catalogName,
        int costCredits,
        int costPoints,
        int pointsType,
        int amount,
        int limitedStack,
        int orderNumber,
        int offerIdClient,
        int songId,
        String extradata,
        boolean haveOffer,
        boolean clubOnly) {
        this(
            CatalogPageType.NORMAL,
            offerId,
            itemIds,
            pageId,
            catalogName,
            costCredits,
            costPoints,
            pointsType,
            amount,
            limitedStack,
            orderNumber,
            offerIdClient,
            songId,
            extradata,
            haveOffer,
            clubOnly);
    }
}
