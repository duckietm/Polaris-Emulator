package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogPurchaseMathTest {
    @Test
    void calculatesNormalPricesWithoutChangingTheirValue() {
        assertEquals(500, CatalogPurchaseMath.checkedPrice(100, 5));
        assertEquals(110, CatalogPurchaseMath.checkedAdd(100, 10));
        assertEquals(2_592_000, CatalogPurchaseMath.checkedSubscriptionSeconds(30));
    }

    @Test
    void rejectsTheConfirmedBillionCreditMultiplicationOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedPrice(1_000_000_000, 3));
    }

    @Test
    void rejectsGiftWrapAdditionOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedAdd(Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsNegativePricesBeforeTheyCanBecomeWalletCredits() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedPrice(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedAdd(0, -1));
    }

    @Test
    void rejectsSubscriptionDurationOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedSubscriptionSeconds(25_000));
    }
}
