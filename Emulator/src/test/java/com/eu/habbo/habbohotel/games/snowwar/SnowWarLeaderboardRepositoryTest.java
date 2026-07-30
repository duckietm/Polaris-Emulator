package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class SnowWarLeaderboardRepositoryTest {

    @Test
    void usesUtcIsoWeeksAndARealResetDeadline() {
        assertEquals(DayOfWeek.MONDAY, SnowWarLeaderboardRepository.weekStart(0).getDayOfWeek());
        assertEquals(
                1,
                ChronoUnit.WEEKS.between(
                        SnowWarLeaderboardRepository.weekStart(1), SnowWarLeaderboardRepository.weekStart(0)));
        assertTrue(SnowWarLeaderboardRepository.minutesUntilReset() >= 0);
        assertTrue(SnowWarLeaderboardRepository.minutesUntilReset() <= 7 * 24 * 60);
    }
}
