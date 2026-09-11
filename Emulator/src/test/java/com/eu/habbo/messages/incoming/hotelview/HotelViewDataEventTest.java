package com.eu.habbo.messages.incoming.hotelview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

/** AIR 13 GetCurrentTimingCode: the entry that is due wins, not the first one. */
class HotelViewDataEventTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Test
    void picksTheLatestEntryThatAlreadyPassed() {
        String schedule = "2001-01-01 00:00,first;" + past(2) + ",second;" + future() + ",third";

        assertEquals("second", HotelViewDataEvent.currentCode(schedule));
    }

    @Test
    void ignoresEntriesInTheFuture() {
        assertEquals("", HotelViewDataEvent.currentCode(future() + ",later"));
    }

    @Test
    void readsASingleEntryWithoutASeparator() {
        assertEquals("only", HotelViewDataEvent.currentCode("2001-01-01 00:00,only"));
    }

    @Test
    void answersAnEmptyCodeForRubbish() {
        assertEquals("", HotelViewDataEvent.currentCode(""));
        assertEquals("", HotelViewDataEvent.currentCode("no-separator"));
        assertEquals("", HotelViewDataEvent.currentCode("not-a-date,code"));
        assertEquals("", HotelViewDataEvent.currentCode(null));
    }

    private static String past(int days) {
        return LocalDateTime.now().minusDays(days).format(FORMAT);
    }

    private static String future() {
        return LocalDateTime.now().plusDays(1).format(FORMAT);
    }
}
