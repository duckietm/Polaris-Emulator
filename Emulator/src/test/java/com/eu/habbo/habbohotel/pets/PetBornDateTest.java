package com.eu.habbo.habbohotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class PetBornDateTest {
    private static int at(String instant) {
        return (int) Instant.parse(instant).getEpochSecond();
    }

    @Test
    void theCreationTimeIsReadAsSecondsNotMilliseconds() {
        assertEquals("20/10/2026", Pet.formatBornDate(at("2026-10-20T12:00:00Z"), TimeZone.getTimeZone("UTC")));
    }

    @Test
    void theMonthCountsFromOne() {
        assertEquals("5/1/2026", Pet.formatBornDate(at("2026-01-05T08:30:00Z"), TimeZone.getTimeZone("UTC")));
        assertEquals("31/12/2025", Pet.formatBornDate(at("2025-12-31T23:59:59Z"), TimeZone.getTimeZone("UTC")));
    }

    @Test
    void theDateFollowsTheTimeZoneOfTheHotel() {
        int created = at("2026-10-20T23:30:00Z");

        assertEquals("20/10/2026", Pet.formatBornDate(created, TimeZone.getTimeZone("UTC")));
        assertEquals("21/10/2026", Pet.formatBornDate(created, TimeZone.getTimeZone("Europe/Rome")));
    }
}
