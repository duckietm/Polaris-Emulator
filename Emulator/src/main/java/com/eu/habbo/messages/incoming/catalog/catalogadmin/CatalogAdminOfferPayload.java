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
                || (pageType != CatalogPageType.BUILDER && cleanItemIds.isEmpty())
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

    int[] baseItemIds() {
        if (this.itemIds.isEmpty()) return new int[0];

        String[] entries = this.itemIds.split(";");
        int[] ids = new int[entries.length];
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            int separator = entry.indexOf(':');
            ids[index] = Integer.parseInt(separator >= 0 ? entry.substring(0, separator) : entry);
        }
        return ids;
    }

    private static String normalizeItemIds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String clean = value.trim().replaceAll("\\s+", "");
        String[] parts = clean.split("[;,]");
        if (clean.length() > MAX_ITEM_IDS_LENGTH) {
            return null;
        }

        if (parts.length == 0 || parts.length > MAX_ITEM_IDS) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                return null;
            }

            String[] itemAndQuantity = part.split(":", -1);
            if (itemAndQuantity.length > 2) {
                return null;
            }

            try {
                int itemId = Integer.parseInt(itemAndQuantity[0]);
                if (itemId <= 0) return null;

                if (itemAndQuantity.length == 2) {
                    int quantity = Integer.parseInt(itemAndQuantity[1]);
                    if (!isInRange(quantity, 1, MAX_AMOUNT)) return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }

            if (normalized.length() > 0) normalized.append(';');
            normalized.append(part);
        }

        return normalized.toString();
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
