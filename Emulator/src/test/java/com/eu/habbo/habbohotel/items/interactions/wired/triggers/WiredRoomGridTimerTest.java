package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** At Given Time counts its pulses from the first 500 ms room tick after the reset. */
class WiredRoomGridTimerTest {

    @Test
    void fourPulsesFireTwoSecondsAfterTheFirstRoomTick() {
        WiredRoomGridTimer timer = new WiredRoomGridTimer();
        // Room ticks at 10 000, 10 500, ...; the reset comes 20 ms after one.
        timer.reset(10_020L);

        assertFalse(timer.isDue(10_000L, 12_479L, 2000L));
        assertTrue(timer.isDue(10_000L, 12_500L, 2000L)); // 2.48 s after the reset
    }

    @Test
    void theFirstTickIsFoundOnTheGridAlsoWhenTicksRanSince() {
        assertEquals(10_500L, WiredRoomGridTimer.firstTickAfter(10_000L, 10_020L));
        assertEquals(10_500L, WiredRoomGridTimer.firstTickAfter(11_000L, 10_020L));
        assertEquals(11_000L, WiredRoomGridTimer.firstTickAfter(10_500L, 10_500L));
        assertEquals(10_520L, WiredRoomGridTimer.firstTickAfter(0L, 10_020L));
    }
}
