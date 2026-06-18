package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;

final class MarketplaceInputGuard {
    static final int MAX_SEARCH_LENGTH = 30;
    static final int DEFAULT_SORT = 1;
    static final int MIN_SORT = 1;
    static final int MAX_SORT = 6;

    private MarketplaceInputGuard() {
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static String normalizeSearch(String query) {
        if (query == null) {
            return "";
        }

        String normalized = query.trim();
        return normalized.length() > MAX_SEARCH_LENGTH ? normalized.substring(0, MAX_SEARCH_LENGTH) : normalized;
    }

    static int normalizeSort(int sort) {
        return sort >= MIN_SORT && sort <= MAX_SORT ? sort : DEFAULT_SORT;
    }

    static int normalizeMinPrice(int minPrice) {
        if (minPrice == -1) {
            return -1;
        }

        return Math.max(0, Math.min(minPrice, MarketPlace.MAXIMUM_LISTING_PRICE));
    }

    static int normalizeMaxPrice(int maxPrice, int minPrice) {
        if (maxPrice == -1) {
            return -1;
        }

        int normalized = Math.max(0, Math.min(maxPrice, MarketPlace.MAXIMUM_LISTING_PRICE));
        return minPrice > 0 && normalized > 0 && normalized < minPrice ? minPrice : normalized;
    }
}
