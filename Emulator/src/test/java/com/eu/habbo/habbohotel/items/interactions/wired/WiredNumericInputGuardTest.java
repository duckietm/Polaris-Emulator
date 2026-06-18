package com.eu.habbo.habbohotel.items.interactions.wired;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredNumericInputGuardTest {

    @Test
    void rejectsInvalidOrNonPositiveAmounts() {
        assertEquals(0, WiredNumericInputGuard.parsePositiveAmount(null, 100));
        assertEquals(0, WiredNumericInputGuard.parsePositiveAmount("nope", 100));
        assertEquals(0, WiredNumericInputGuard.parsePositiveAmount("0", 100));
        assertEquals(0, WiredNumericInputGuard.parsePositiveAmount("-5", 100));
    }

    @Test
    void clampsAmountsToConfiguredMaximum() {
        assertEquals(50, WiredNumericInputGuard.parsePositiveAmount("50", 100));
        assertEquals(100, WiredNumericInputGuard.parsePositiveAmount("500", 100));
    }

    @Test
    void appliesAbsoluteMaximumEvenWhenConfiguredTooHigh() {
        assertEquals(WiredNumericInputGuard.MAX_ABSOLUTE_AMOUNT,
                WiredNumericInputGuard.parsePositiveAmount("999999999", Integer.MAX_VALUE));
    }
}
