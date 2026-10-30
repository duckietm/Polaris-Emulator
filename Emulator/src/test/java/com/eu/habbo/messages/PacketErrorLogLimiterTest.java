package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Every handler exception was logged with its stack trace, so one client repeating a packet its handler could not
 * take wrote several stack traces a second. Now one packet and exception type is logged once a window.
 */
class PacketErrorLogLimiterTest {

    @Test
    void theFirstExceptionIsLoggedAndTheRepeatsInTheWindowAreCounted() {
        PacketErrorLogLimiter limiter = new PacketErrorLogLimiter(60_000);

        assertTrue(limiter.record(100, NullPointerException.class, 0).log());
        assertFalse(limiter.record(100, NullPointerException.class, 1_000).log());
        assertFalse(limiter.record(100, NullPointerException.class, 2_000).log());

        PacketErrorLogLimiter.Decision next = limiter.record(100, NullPointerException.class, 60_000);
        assertTrue(next.log());
        assertEquals(2, next.suppressedSinceLast(), "the line after the window reports the repeats it stood for");
    }

    @Test
    void anotherPacketOrAnotherExceptionIsLoggedOnItsOwn() {
        PacketErrorLogLimiter limiter = new PacketErrorLogLimiter(60_000);

        assertTrue(limiter.record(100, NullPointerException.class, 0).log());
        assertTrue(limiter.record(101, NullPointerException.class, 0).log());
        assertTrue(limiter.record(100, IllegalStateException.class, 0).log());
    }

    @Test
    void theTrackedPairsStayBounded() {
        PacketErrorLogLimiter limiter = new PacketErrorLogLimiter(60_000);

        for (int header = 0; header < PacketErrorLogLimiter.MAX_TRACKED * 3; header++) {
            assertTrue(limiter.record(header, RuntimeException.class, 0).log());
        }
    }
}
