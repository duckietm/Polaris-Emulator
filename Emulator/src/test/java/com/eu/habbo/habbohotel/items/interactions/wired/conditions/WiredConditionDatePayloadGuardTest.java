package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionDatePayloadGuardTest {
    @Test
    void matchDateBoundsMasksAndParts() {
        WiredConditionMatchDate condition = new WiredConditionMatchDate(1, 1, null, "", 0, 0);

        assertEquals(0, condition.normalizeMode(99));
        assertEquals(2, condition.normalizeMode(2));
        assertEquals(1, condition.normalizeDay(-1));
        assertEquals(31, condition.normalizeDay(99));
        assertEquals(1, condition.normalizeYear(-1));
        assertEquals(9999, condition.normalizeYear(50_000));
        assertEquals(254, condition.normalizeWeekdayMask(0));
        assertEquals(8190, condition.normalizeMonthMask(0));
    }

    @Test
    void matchTimeBoundsParts() {
        WiredConditionMatchTime condition = new WiredConditionMatchTime(1, 1, null, "", 0, 0);

        assertEquals(0, condition.normalizeMode(99));
        assertEquals(2, condition.normalizeMode(2));
        assertEquals(0, condition.normalizeHour(-1));
        assertEquals(23, condition.normalizeHour(99));
        assertEquals(0, condition.normalizeMinuteOrSecond(-1));
        assertEquals(59, condition.normalizeMinuteOrSecond(99));
    }

    @Test
    void dateRangeBoundsAndSortsUnixTimestamps() {
        assertEquals(0, WiredDateRangeInputGuard.normalizeTimestamp(-1));
        assertEquals(123, WiredDateRangeInputGuard.normalizeTimestamp(123));

        int[] range = WiredDateRangeInputGuard.normalizeRange(200, 100);

        assertEquals(0, range[0]);
        assertEquals(0, range[1]);
    }
}
