package com.eu.habbo.habbohotel.items.interactions.wired;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredTimerInputGuardTest {

    @Test
    void clientTimerUnitsAreMultipliedWithoutOverflow() {
        assertEquals(500, WiredTimerInputGuard.fromClientUnits(1, 500, 500));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
                WiredTimerInputGuard.fromClientUnits(Integer.MAX_VALUE, 5000, 5000));
    }

    @Test
    void invalidClientTimerUnitsUseMinimumDelay() {
        assertEquals(500, WiredTimerInputGuard.fromClientUnits(0, 500, 500));
        assertEquals(5000, WiredTimerInputGuard.fromClientUnits(-10, 5000, 5000));
    }

    @Test
    void storedTimerValuesFallbackOrClamp() {
        assertEquals(10000, WiredTimerInputGuard.normalizeStoredMillis(null, 500, 10000));
        assertEquals(10000, WiredTimerInputGuard.normalizeStoredMillis(-1, 500, 10000));
        assertEquals(500, WiredTimerInputGuard.normalizeStoredMillis(500, 500, 10000));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
                WiredTimerInputGuard.normalizeStoredMillis(Integer.MAX_VALUE, 500, 10000));
    }

    @Test
    void shortRepeaterKeepsItsLegacyMaximum() {
        assertEquals(500, WiredTimerInputGuard.fromClientUnits(Integer.MAX_VALUE, 50, 50, 500));
    }
}
