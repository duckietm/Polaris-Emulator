package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmulatorTimeCompatibilityTest {

    @Test
    void durationParserRetainsLegacyUnitAndInvalidInputBehavior() {
        assertEquals(
                90,
                Emulator.timeStringToSeconds(
                        "1 minute 30 second"));
        assertEquals(0, Emulator.timeStringToSeconds("invalid"));
    }

    @Test
    void durationParserSaturatesInsteadOfOverflowing() {
        assertEquals(
                Integer.MAX_VALUE,
                Emulator.timeStringToSeconds("100 year"));
    }

    @Test
    void dateModificationRetainsCalendarUnitBehavior() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 15, 12, 0, 0);

        Date modified = Emulator.modifyDate(
                calendar.getTime(), "1 month 2 day");

        calendar.setTime(modified);
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH));
        assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    void legacyDateParserStillReturnsNullForInvalidInput() {
        assertNull(Emulator.stringToDate("not a date"));
    }

}
