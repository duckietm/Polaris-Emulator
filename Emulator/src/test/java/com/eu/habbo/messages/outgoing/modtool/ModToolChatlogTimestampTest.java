package com.eu.habbo.messages.outgoing.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ModToolChatlogTimestampTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Test
    void aLineFromTodayIsJustTheTime() {
        Instant today =
                LocalDate.now(ZONE).atTime(LocalTime.of(12, 4)).atZone(ZONE).toInstant();

        assertEquals("12:04", ModToolChatlogTimestamp.format(today));
    }

    /** A report about yesterday used to read "12:04" with no way to tell which day that was. */
    @Test
    void anOlderLineCarriesTheDay() {
        Instant yesterday = LocalDate.now(ZONE)
                .minusDays(1)
                .atTime(LocalTime.of(12, 4))
                .atZone(ZONE)
                .toInstant();

        String stamped = ModToolChatlogTimestamp.format(yesterday);

        assertTrue(stamped.endsWith(" 12:04"), () -> "got " + stamped);
        assertEquals(11, stamped.length(), () -> "got " + stamped);
    }

    @Test
    void secondsSinceTheEpochAreAcceptedToo() {
        long epochSeconds =
                LocalDate.now(ZONE).atTime(LocalTime.of(9, 30)).atZone(ZONE).toEpochSecond();

        assertEquals("09:30", ModToolChatlogTimestamp.format(epochSeconds));
    }

    @Test
    void aMissingInstantIsEmptyRatherThanACrash() {
        assertEquals("", ModToolChatlogTimestamp.format((Instant) null));
    }
}
