package com.eu.habbo.messages.incoming.trading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeItemIdGuardTest {
    @Test
    void acceptsOnlyPositiveUniqueIds() {
        assertTrue(TradeItemIdGuard.arePositiveAndUnique(new int[]{10, 20, 30}));
        assertFalse(TradeItemIdGuard.arePositiveAndUnique(new int[]{10, 0, 30}));
        assertFalse(TradeItemIdGuard.arePositiveAndUnique(new int[]{10, -1, 30}));
        assertFalse(TradeItemIdGuard.arePositiveAndUnique(new int[]{10, 20, 10}));
        assertFalse(TradeItemIdGuard.arePositiveAndUnique(new int[]{}));
        assertFalse(TradeItemIdGuard.arePositiveAndUnique(null));
    }
}
