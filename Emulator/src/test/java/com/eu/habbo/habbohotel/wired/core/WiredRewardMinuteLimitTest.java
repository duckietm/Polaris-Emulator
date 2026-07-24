package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredRewardMinuteLimitTest {

    @Test
    void usesTheConfiguredMinuteInterval() {
        assertTrue(WiredManager.isWithinMinuteLimit(1_000, 900, 3));
        assertTrue(WiredManager.isWithinMinuteLimit(1_000, 820, 3));
        assertFalse(WiredManager.isWithinMinuteLimit(1_000, 819, 3));
    }
}
