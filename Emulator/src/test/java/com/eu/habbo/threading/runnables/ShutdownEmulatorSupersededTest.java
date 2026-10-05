package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShutdownEmulatorSupersededTest {
    @Test
    void aCountdownStillPendingExits() {
        assertFalse(ShutdownEmulator.isSuperseded(1_000, 1_000));
    }

    @Test
    void aCancelledCountdownDoesNotExit() {
        assertTrue(ShutdownEmulator.isSuperseded(1_000, 0));
    }

    @Test
    void aReplacedCountdownDoesNotExit() {
        assertTrue(ShutdownEmulator.isSuperseded(1_000, 1_300));
    }

    @Test
    void aTaskWithoutDeadlineAlwaysExits() {
        assertFalse(ShutdownEmulator.isSuperseded(0, 0));
        assertFalse(ShutdownEmulator.isSuperseded(0, 1_300));
    }

    @Test
    void clearPendingResetsTheCountdown() {
        boolean previousInstantiated = ShutdownEmulator.instantiated;
        int previousTimestamp = ShutdownEmulator.timestamp;

        try {
            ShutdownEmulator.instantiated = true;
            ShutdownEmulator.timestamp = 1_234;

            ShutdownEmulator.clearPending();

            assertEquals(0, ShutdownEmulator.timestamp);
            assertFalse(ShutdownEmulator.instantiated);
        } finally {
            ShutdownEmulator.instantiated = previousInstantiated;
            ShutdownEmulator.timestamp = previousTimestamp;
        }
    }
}
