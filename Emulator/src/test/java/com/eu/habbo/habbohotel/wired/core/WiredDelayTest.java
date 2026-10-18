package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredDelayTest {

    @Test
    void pulsesAndPreciseMillisecondsBothBecomeTime() {
        assertEquals(0L, WiredDelay.toMillis(0));
        assertEquals(1500L, WiredDelay.toMillis(3));
        assertEquals(150L, WiredDelay.toMillis(-150));
        assertTrue(WiredDelay.isDelayed(-50));
        assertFalse(WiredDelay.isDelayed(0));
    }

    @Test
    void aPreciseDelayKeepsItsStepsWithinTheSameTimeLimit() {
        assertEquals(-150, WiredDelay.normalize(-150, 120));
        assertEquals(-150, WiredDelay.normalize(-160, 120));
        assertEquals(-60000, WiredDelay.normalize(-90000, 120));
        // 20 pulses is 10 seconds.
        assertEquals(-10000, WiredDelay.normalize(-20000, 20));
        assertEquals(20, WiredDelay.normalize(40, 20));
        assertTrue(WiredDelay.isValid(-500, 20));
        assertFalse(WiredDelay.isValid(-20000, 20));
        assertFalse(WiredDelay.isValid(-160, 20));
    }
}
