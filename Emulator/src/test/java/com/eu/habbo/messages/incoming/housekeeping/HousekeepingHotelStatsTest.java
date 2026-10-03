package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HousekeepingHotelStatsTest {
    @Test
    void activityCountsDistinctUsersPerHourAndBansPerDay() {
        assertTrue(HousekeepingHotelStats.ACTIVITY_SQL.contains("COUNT(DISTINCT user_id)"));
        assertTrue(HousekeepingHotelStats.ACTIVITY_SQL.contains("/ 3600) * 3600"));
        assertTrue(HousekeepingHotelStats.ACTIVITY_SQL.contains("FROM room_enter_log"));
        assertTrue(HousekeepingHotelStats.BANS_SQL.contains("/ 86400) * 86400"));
        assertTrue(HousekeepingHotelStats.BANS_SQL.contains("FROM bans"));
    }

    @Test
    void theWindowsAreADayOfHoursAndTwoWeeksOfDays() {
        assertEquals(24, HousekeepingHotelStats.ACTIVITY_HOURS);
        assertEquals(14, HousekeepingHotelStats.BAN_DAYS);
        assertEquals("activity", HousekeepingHotelStats.SERIES_ACTIVITY);
        assertEquals("bans", HousekeepingHotelStats.SERIES_BANS);
    }
}
