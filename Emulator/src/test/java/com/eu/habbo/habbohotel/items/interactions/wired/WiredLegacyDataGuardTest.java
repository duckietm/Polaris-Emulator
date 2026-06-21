package com.eu.habbo.habbohotel.items.interactions.wired;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredLegacyDataGuardTest {

    @Test
    void malformedDelayFallsBackToZero() {
        assertEquals(0, WiredLegacyDataGuard.parseDelay(null));
        assertEquals(0, WiredLegacyDataGuard.parseDelay("nope"));
        assertEquals(0, WiredLegacyDataGuard.parseDelay("-5"));
    }

    @Test
    void oversizedDelayIsClampedThroughWiredInputGuard() {
        assertEquals(WiredLegacyDataGuard.DEFAULT_MAX_DELAY, WiredLegacyDataGuard.parseDelay("999999"));
    }

    @Test
    void nullRoomOrBlankItemsReturnEmptyList() {
        assertEquals(0, WiredLegacyDataGuard.parseRoomItems("1;2;bad", null).size());
        assertEquals(0, WiredLegacyDataGuard.parseRoomItems("", null).size());
    }
}
