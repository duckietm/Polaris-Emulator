package com.eu.habbo.messages.incoming.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetOfferPurchaseGuardTest {
    @Test
    void rejectsInvalidRequestedAmounts() {
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(0, 10, 0));
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(-5, 10, 0));
    }

    @Test
    void rejectsOffersWithoutPositiveLimits() {
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(1, 0, 0));
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(1, -10, 0));
    }

    @Test
    void capsAmountToRemainingLimit() {
        assertEquals(3, TargetOfferPurchaseGuard.purchasableAmount(10, 5, 2));
    }

    @Test
    void rejectsWhenLimitIsAlreadyConsumed() {
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(1, 5, 5));
        assertEquals(0, TargetOfferPurchaseGuard.purchasableAmount(1, 5, 8));
    }

    @Test
    void doesNotLetNegativePurchaseHistoryIncreaseRemainingLimit() {
        assertEquals(5, TargetOfferPurchaseGuard.purchasableAmount(10, 5, -4));
    }
}
