package com.eu.habbo.habbohotel.messenger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsoleMessageThrottleTest {

    @Test
    void firstMessageIsAllowedAndAQuickSecondOneIsRefused() {
        ConsoleMessageThrottle throttle = new ConsoleMessageThrottle();

        assertFalse(throttle.flooded(10_000));
        assertTrue(throttle.flooded(10_000 + ConsoleMessageThrottle.MIN_INTERVAL_MS - 1));
    }

    @Test
    void refusedMessagesDoNotExtendTheWindow() {
        ConsoleMessageThrottle throttle = new ConsoleMessageThrottle();

        assertFalse(throttle.flooded(10_000));
        assertTrue(throttle.flooded(10_500));
        assertFalse(throttle.flooded(10_000 + ConsoleMessageThrottle.MIN_INTERVAL_MS));
    }

    @Test
    void eachUserHasItsOwnWindow() {
        ConsoleMessageThrottle first = new ConsoleMessageThrottle();
        ConsoleMessageThrottle second = new ConsoleMessageThrottle();

        assertFalse(first.flooded(10_000));
        assertFalse(second.flooded(10_000));
    }
}
