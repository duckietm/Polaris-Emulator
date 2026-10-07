package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HabboStatsMuteTimeTest {
    private static final int NOW = 1_790_000_000;

    @Test
    void aNewMuteStartsFromNow() {
        assertEquals(NOW + 60, HabboStats.extendMuteEnd(0, NOW, 60));
        assertEquals(NOW + 60, HabboStats.extendMuteEnd(NOW - 10, NOW, 60));
    }

    @Test
    void aRunningMuteIsExtended() {
        assertEquals(NOW + 160, HabboStats.extendMuteEnd(NOW + 100, NOW, 60));
    }

    @Test
    void aVeryLongMuteIsClampedInsteadOfOverflowing() {
        int end = HabboStats.extendMuteEnd(0, NOW, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, end);
        assertEquals(Integer.MAX_VALUE - NOW, HabboStats.remainingMuteSeconds(end, NOW));
    }

    @Test
    void extendingAClampedMuteStaysClamped() {
        assertEquals(Integer.MAX_VALUE, HabboStats.extendMuteEnd(Integer.MAX_VALUE, NOW, 3600));
    }

    @Test
    void negativeDurationsDoNotShortenAMute() {
        assertEquals(NOW + 100, HabboStats.extendMuteEnd(NOW + 100, NOW, -50));
    }

    @Test
    void remainingTimeNeverUnderflows() {
        assertEquals(0, HabboStats.remainingMuteSeconds(0, NOW));
        assertEquals(0, HabboStats.remainingMuteSeconds(Integer.MIN_VALUE, NOW));
        assertEquals(30, HabboStats.remainingMuteSeconds(NOW + 30, NOW));
    }
}
