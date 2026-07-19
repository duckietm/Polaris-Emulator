package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmulatorTimeUtilityTest {

    @Test
    void durationParserPreservesLegacyTokenRules() {
        assertEquals(
                239_900_521,
                Emulator.timeStringToSeconds(
                        "1 second 2 minutes 3 hour 4 day 5 week 6 month 7 year"));
        assertEquals(1, Emulator.timeStringToSeconds("1 seconds"));
        assertEquals(0, Emulator.timeStringToSeconds("1 SECOND"));
        assertEquals(0, Emulator.timeStringToSeconds("invalid"));
        assertThrows(
                NullPointerException.class,
                () -> Emulator.timeStringToSeconds(null));
    }

    @Test
    void dateModifierPreservesCalendarArithmetic() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date initial = format.parse("2024-01-31 12:00:00");

            assertEquals(
                    format.parse("2024-03-07 14:00:00"),
                    Emulator.modifyDate(initial, "1 month 1 week 2 hour"));
            assertEquals(initial, Emulator.modifyDate(initial, "INVALID"));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void publicDateParserPreservesLegacyNullAndLenientBehavior() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            assertEquals(
                    format.parse("2024-02-29 01:02:03"),
                    Emulator.stringToDate("2024-02-29 01:02:03"));
            assertEquals(
                    format.parse("2024-03-01 01:02:03"),
                    Emulator.stringToDate("2024-02-30 01:02:03 trailing"));
            assertNull(Emulator.stringToDate("not-a-date"));
            assertNull(Emulator.stringToDate(null));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void legacyTimeMethodDescriptorsRemainIntBased() throws Exception {
        assertEquals(
                int.class,
                Emulator.class
                        .getDeclaredMethod("timeStringToSeconds", String.class)
                        .getReturnType());
        assertEquals(
                int.class,
                Emulator.class.getDeclaredMethod("getIntUnixTimestamp").getReturnType());
        assertEquals(
                int.class,
                Emulator.class.getDeclaredMethod("getTimeStarted").getReturnType());
        assertEquals(
                int.class,
                Emulator.class.getDeclaredMethod("getOnlineTime").getReturnType());
    }
}
