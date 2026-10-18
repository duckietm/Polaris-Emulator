package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A front page slot sends the seconds until it expires, as the client reads it. */
class CatalogFeaturedPageExpiryTest {

    @Test
    void aSlotSendsTheSecondsLeftAndZeroWhenItNeverOrNoLongerExpires() {
        assertEquals(600, CatalogFeaturedPage.secondsLeft(1_000_600, 1_000_000));
        assertEquals(0, CatalogFeaturedPage.secondsLeft(0, 1_000_000));
        assertEquals(0, CatalogFeaturedPage.secondsLeft(999_000, 1_000_000));
    }
}
