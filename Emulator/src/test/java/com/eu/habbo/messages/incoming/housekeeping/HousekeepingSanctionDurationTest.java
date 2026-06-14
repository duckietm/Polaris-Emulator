package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HousekeepingSanctionDurationTest {
    @Test
    void convertsHoursAndMinutesWithoutIntegerOverflow() {
        assertEquals(3600, HousekeepingSanctionDuration.secondsFromHours(1));
        assertEquals(60, HousekeepingSanctionDuration.secondsFromMinutes(1));
        assertEquals(Integer.MAX_VALUE, HousekeepingSanctionDuration.secondsFromHours(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, HousekeepingSanctionDuration.secondsFromMinutes(Integer.MAX_VALUE));
    }

    @Test
    void capsUnixTimestampInsteadOfWrapping() {
        assertEquals(1_000_060, HousekeepingSanctionDuration.unixUntil(1_000_000, 60));
        assertEquals(Integer.MAX_VALUE, HousekeepingSanctionDuration.unixUntil(Integer.MAX_VALUE - 10, 60));
    }
}
