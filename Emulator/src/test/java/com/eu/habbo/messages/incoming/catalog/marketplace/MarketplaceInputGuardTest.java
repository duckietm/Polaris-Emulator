package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceInputGuardTest {
    @Test
    void idsMustBePositive() {
        assertFalse(MarketplaceInputGuard.isPositiveId(0));
        assertFalse(MarketplaceInputGuard.isPositiveId(-1));
        assertTrue(MarketplaceInputGuard.isPositiveId(1));
    }

    @Test
    void searchIsTrimmedAndBounded() {
        assertEquals("", MarketplaceInputGuard.normalizeSearch(null));
        assertEquals("rare", MarketplaceInputGuard.normalizeSearch("  rare  "));
        assertEquals(MarketplaceInputGuard.MAX_SEARCH_LENGTH, MarketplaceInputGuard.normalizeSearch("a".repeat(80)).length());
    }

    @Test
    void sortFallsBackToDefaultOutsideKnownRange() {
        assertEquals(MarketplaceInputGuard.DEFAULT_SORT, MarketplaceInputGuard.normalizeSort(0));
        assertEquals(3, MarketplaceInputGuard.normalizeSort(3));
        assertEquals(MarketplaceInputGuard.DEFAULT_SORT, MarketplaceInputGuard.normalizeSort(7));
    }

    @Test
    void priceRangesPreserveCacheSentinelAndStayBounded() {
        assertEquals(-1, MarketplaceInputGuard.normalizeMinPrice(-1));
        assertEquals(0, MarketplaceInputGuard.normalizeMinPrice(-100));
        assertEquals(MarketPlace.MAXIMUM_LISTING_PRICE, MarketplaceInputGuard.normalizeMinPrice(Integer.MAX_VALUE));

        assertEquals(-1, MarketplaceInputGuard.normalizeMaxPrice(-1, -1));
        assertEquals(500, MarketplaceInputGuard.normalizeMaxPrice(100, 500));
        assertEquals(MarketPlace.MAXIMUM_LISTING_PRICE, MarketplaceInputGuard.normalizeMaxPrice(Integer.MAX_VALUE, 0));
    }
}
