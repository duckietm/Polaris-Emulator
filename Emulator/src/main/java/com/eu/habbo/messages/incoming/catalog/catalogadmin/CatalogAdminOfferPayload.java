package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;

final class CatalogAdminOfferPayload {
    private static final int MAX_ITEM_IDS_LENGTH = 512;
    private static final int MAX_ITEM_IDS = 100;
    private static final int MAX_CATALOG_NAME_LENGTH = 128;
    private static final int MAX_EXTRADATA_LENGTH = 1024;
    private static final int MAX_CURRENCY_VALUE = 1_000_000_000;
    private static final int MAX_AMOUNT = 10_000;
    private static final int MAX_POINTS_TYPE = 10_000;
    private static final int MAX_ORDER_NUMBER = 1_000_000;
    private static final int MAX_LIMITED_STACK = 1_000_000;

    final int pageId;
    final String itemIds;
    final String catalogName;
    final int costCredits;
    final int costPoints;
    final int pointsType;
    final int amount;
    final int clubOnly;
    final String extradata;
    final boolean haveOffer;
    final int offerIdGroup;
    final int limitedStack;
    final int orderNumber;
    final CatalogPageType pageType;

    private CatalogAdminOfferPayload(int pageId, String itemIds, String catalogName, int costCredits, int costPoints,
                                     int pointsType, int amount, int clubOnly, String extradata, boolean haveOffer,
                                     int offerIdGroup, int limitedStack, int orderNumber, CatalogPageType pageType) {
        this.pageId = pageId;
        this.itemIds = itemIds;
        this.catalogName = catalogName;
        this.costCredits = costCredits;
        this.costPoints = costPoints;
        this.pointsType = pointsType;
        this.amount = amount;
        this.clubOnly = clubOnly;
        this.extradata = extradata;
        this.haveOffer = haveOffer;
        this.offerIdGroup = offerIdGroup;
        this.limitedStack = limitedStack;
        this.orderNumber = orderNumber;
        this.pageType = pageType;
    }

    static CatalogAdminOfferPayload validate(int pageId, String itemIds, String catalogName, int costCredits,
                                             int costPoints, int pointsType, int amount, int clubOnly,
                                             String extradata, boolean haveOffer, int offerIdGroup,
                                             int limitedStack, int orderNumber, CatalogPageType pageType) {
        String cleanItemIds = normalizeItemIds(itemIds);
        String cleanCatalogName = clamp(catalogName, MAX_CATALOG_NAME_LENGTH);
        String cleanExtradata = clamp(extradata, MAX_EXTRADATA_LENGTH);

        if (pageId <= 0
                || cleanItemIds == null
                || cleanCatalogName.isBlank()
                || !isInRange(orderNumber, 0, MAX_ORDER_NUMBER)) {
            return null;
        }

        if (pageType != CatalogPageType.BUILDER) {
            if (!isInRange(costCredits, 0, MAX_CURRENCY_VALUE)
                    || !isInRange(costPoints, 0, MAX_CURRENCY_VALUE)
                    || !isInRange(pointsType, 0, MAX_POINTS_TYPE)
                    || !isInRange(amount, 1, MAX_AMOUNT)
                    || !isInRange(clubOnly, 0, 1)
                    || offerIdGroup < 0
                    || !isInRange(limitedStack, 0, MAX_LIMITED_STACK)) {
                return null;
            }
        }

        return new CatalogAdminOfferPayload(pageId, cleanItemIds, cleanCatalogName, costCredits, costPoints,
                pointsType, amount, clubOnly, cleanExtradata, haveOffer, offerIdGroup, limitedStack, orderNumber,
                pageType);
    }

    private static String normalizeItemIds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        String clean = value.trim();
        if (clean.length() > MAX_ITEM_IDS_LENGTH) {
            return null;
        }

        String[] parts = clean.split(",");
        if (parts.length == 0 || parts.length > MAX_ITEM_IDS) {
            return null;
        }

        for (String part : parts) {
            if (part.isBlank()) {
                return null;
            }

            try {
                if (Integer.parseInt(part.trim()) < 0) {
                    return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return clean.replaceAll("\\s+", "");
    }

    private static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static String clamp(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
