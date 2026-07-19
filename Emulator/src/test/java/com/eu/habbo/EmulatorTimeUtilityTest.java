package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void durationParserSaturatesInsteadOfOverflowing() {
        assertEquals(
                Integer.MAX_VALUE,
                Emulator.timeStringToSeconds("100 year"));
        assertEquals(
                Integer.MAX_VALUE,
                Emulator.timeStringToSeconds("68 year 10 year"));
    }

    @Test
    void internalEpochCalculationsRemainValidPastTheIntBoundary() throws Exception {
        assertEquals(long.class, Emulator.class.getDeclaredField("timeStarted").getType());

        Method longTimestamp = Emulator.class.getDeclaredMethod("getLongUnixTimestamp");
        assertEquals(long.class, longTimestamp.getReturnType());
        assertTrue((long) longTimestamp.invoke(null) > 0L);

        Method elapsedSeconds =
                Emulator.class.getDeclaredMethod("elapsedSeconds", long.class, long.class);
        elapsedSeconds.setAccessible(true);
        assertEquals(
                20,
                elapsedSeconds.invoke(null, 2_147_483_640L, 2_147_483_660L));
        assertEquals(
                Integer.MAX_VALUE,
                elapsedSeconds.invoke(null, 0L, 3_000_000_000L));
    }

    @Test
    void timestampParsingAndFormattingUseImmutableFormatters() throws Exception {
        assertEquals(
                DateTimeFormatter.class,
                Emulator.class
                        .getDeclaredField("BUILD_TIMESTAMP_FORMAT")
                        .getType());
        assertEquals(
                DateTimeFormatter.class,
                Emulator.class
                        .getDeclaredField("LEGACY_TIMESTAMP_PARSER")
                        .getType());

        Method formatter =
                Emulator.class.getDeclaredMethod(
                        "formatBuildTimestamp", long.class, String.class);
        formatter.setAccessible(true);
        assertEquals(
                "2023-11-14 22:13:20",
                formatter.invoke(null, 1_700_000_000_000L, "UTC"));
        assertEquals("UNKNOWN", formatter.invoke(null, -1L, "UTC"));
    }
}
