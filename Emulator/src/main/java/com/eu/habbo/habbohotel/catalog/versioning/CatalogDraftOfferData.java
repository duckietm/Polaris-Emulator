package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;

public record CatalogDraftOfferData(
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

    CatalogOfferSnapshot withId(int offerId) {
        return withId(CatalogPageType.NORMAL, offerId);
    }

    CatalogOfferSnapshot withId(CatalogPageType catalogType, int offerId) {
        return new CatalogOfferSnapshot(
                catalogType,
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
