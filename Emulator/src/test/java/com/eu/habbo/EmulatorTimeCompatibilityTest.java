package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class EmulatorTimeCompatibilityTest {

    @Test
    void durationParserRetainsLegacyUnitAndInvalidInputBehavior() {
        assertEquals(90, Emulator.timeStringToSeconds("1 minute 30 second"));
        assertEquals(0, Emulator.timeStringToSeconds("invalid"));
    }

    @Test
    void durationParserSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, Emulator.timeStringToSeconds("100 year"));
        assertEquals(Integer.MAX_VALUE, Emulator.timeStringToSeconds("68 year 10 year"));
    }

    @Test
    void durationParserBoundsInvalidDigitRuns() {
        String invalidDuration = "0".repeat(50_000) + " invalid";

        assertTimeout(Duration.ofMillis(500), () -> assertEquals(0, Emulator.timeStringToSeconds(invalidDuration)));
    }

    @Test
    void dateModificationRetainsCalendarUnitBehavior() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 15, 12, 0, 0);

        Date modified = Emulator.modifyDate(calendar.getTime(), "1 month 2 day");

        calendar.setTime(modified);
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH));
        assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    void legacyDateParserStillReturnsNullForInvalidInput() {
        assertNull(Emulator.stringToDate("not a date"));
    }

    @Test
    void legacyDateParserRetainsLenientAndTrailingInputBehavior() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat legacy = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            assertEquals(legacy.parse("2024-03-01 01:02:03"), Emulator.stringToDate("2024-02-30 01:02:03 trailing"));
            assertNull(Emulator.stringToDate(null));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void longEpochHelperDoesNotUseTheLegacyIntRange() {
        long before = System.currentTimeMillis() / 1000L;
        long actual = Emulator.getLongUnixTimestamp();
        long after = System.currentTimeMillis() / 1000L;

        assertTrue(actual >= before && actual <= after);
    }

    @Test
    void internalOnlineClockRemainsValidPastIntEpochBoundary() throws Exception {
        assertEquals(long.class, Emulator.class.getDeclaredField("timeStarted").getType());
        Method elapsed = Emulator.class.getDeclaredMethod("elapsedSeconds", long.class, long.class);
        elapsed.setAccessible(true);

        assertEquals(20, elapsed.invoke(null, 2_147_483_640L, 2_147_483_660L));
        assertEquals(Integer.MAX_VALUE, elapsed.invoke(null, 0L, 3_000_000_000L));
    }

    @Test
    void timestampParsingAndFormattingUseImmutableFormatters() throws Exception {
        assertEquals(
                DateTimeFormatter.class,
                Emulator.class.getDeclaredField("BUILD_TIMESTAMP_FORMAT").getType());
        assertEquals(
                DateTimeFormatter.class,
                Emulator.class.getDeclaredField("LEGACY_TIMESTAMP_PARSER").getType());
    }
}
