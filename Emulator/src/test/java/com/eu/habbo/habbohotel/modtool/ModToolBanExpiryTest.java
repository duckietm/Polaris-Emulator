package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModToolBanExpiryTest {
    private static final int NOW = 1_790_622_185;

    @Test
    void aTimedBanEndsAfterItsDuration() {
        assertEquals(NOW + 3600, ModToolManager.banExpiry(NOW, 3600));
    }

    @Test
    void aPermanentBanStaysInTheFutureInsteadOfWrappingNegative() {
        assertEquals(Integer.MAX_VALUE, ModToolManager.banExpiry(NOW, Integer.MAX_VALUE));
    }

    @Test
    void sanctionHoursAreSecondsAndALongLengthDoesNotWrapNegative() {
        assertEquals(18 * 3600, ModToolManager.hoursToSeconds(18));
        assertEquals(Integer.MAX_VALUE, ModToolManager.hoursToSeconds(876000L * 10));
        assertEquals(0, ModToolManager.hoursToSeconds(-5));
    }

    @Test
    void sanctionDaysAreSecondsAndAHundredYearsStaysPositive() {
        assertEquals(7 * 86400, ModToolManager.daysToSeconds(7));
        assertEquals(Integer.MAX_VALUE, ModToolManager.daysToSeconds(36500));
    }
}
