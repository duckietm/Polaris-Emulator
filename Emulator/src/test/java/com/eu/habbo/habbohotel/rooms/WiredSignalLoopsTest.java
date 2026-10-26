package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AIR's signal loop limit: 100 runs per receiving stack per 500 ms window and per chain. */
class WiredSignalLoopsTest {
    @Test
    void aStackRunsAHundredTimesAWindow() {
        WiredSignalLoops loops = new WiredSignalLoops();
        int runs = 0;

        while (loops.admit(7, new HashMap<>(), 1_000L)) {
            runs++;
            if (runs > 1000) break;
        }

        assertEquals(100, runs);
        // Another stack has its own count; the next window starts over.
        assertTrue(loops.admit(8, null, 1_000L));
        assertTrue(loops.admit(7, null, 1_500L));
    }

    @Test
    void aChainStopsAtAHundredOverAWindowsEnd() {
        WiredSignalLoops loops = new WiredSignalLoops();
        Map<Integer, Integer> chain = new HashMap<>();

        for (int i = 0; i < 60; i++) {
            assertTrue(loops.admit(7, chain, 1_400L));
        }
        for (int i = 0; i < 40; i++) {
            assertTrue(loops.admit(7, chain, 1_600L));
        }

        assertFalse(loops.admit(7, chain, 1_600L));
        // A new chain (a delayed Send Signal) goes on.
        assertTrue(loops.admit(7, new HashMap<>(), 1_600L));
    }
}
