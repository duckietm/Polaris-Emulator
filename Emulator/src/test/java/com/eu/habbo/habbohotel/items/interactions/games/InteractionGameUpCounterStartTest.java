package com.eu.habbo.habbohotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A started counter shows its first second on the 2nd 500 ms room boundary after the start. */
class InteractionGameUpCounterStartTest {

    @Test
    void theFirstTickLandsOnTheSecondBoundaryStrictlyAfterTheStart() {
        // Cycle at 10_000: boundaries at 10_500, 11_000, ...
        assertEquals(1000, InteractionGameUpCounter.firstTickDelayMs(10_000, 10_000));
        assertEquals(999, InteractionGameUpCounter.firstTickDelayMs(10_000, 10_001));
        assertEquals(501, InteractionGameUpCounter.firstTickDelayMs(10_000, 10_499));
        assertEquals(1000, InteractionGameUpCounter.firstTickDelayMs(10_000, 10_500));
    }

    @Test
    void withoutARoomCycleItCountsFromTheStart() {
        assertEquals(1000, InteractionGameUpCounter.firstTickDelayMs(0, 5_000));
    }
}
