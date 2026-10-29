package com.eu.habbo.habbohotel.wired.highscores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.util.HotelDateTimeUtil;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * A period was "after its first second and before its last second": a score made at midnight, or in the last
 * second of the day, week or month, counted on no board of that period.
 */
class WiredHighscorePeriodTest {

    /** Wednesday 15 October 2025, mid-afternoon. */
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 10, 15, 15, 30);

    private static long at(LocalDateTime time) {
        return HotelDateTimeUtil.toEpochSecond(time);
    }

    private static boolean counts(LocalDateTime time, WiredHighscoreClearType type) {
        return WiredHighscoreManager.isInCurrentPeriod(at(time), type, NOW, DayOfWeek.MONDAY);
    }

    @Test
    void aDayRunsFromMidnightUpToTheNextMidnight() {
        assertTrue(counts(LocalDateTime.of(2025, 10, 15, 0, 0), WiredHighscoreClearType.DAILY));
        assertTrue(counts(LocalDateTime.of(2025, 10, 15, 23, 59, 59), WiredHighscoreClearType.DAILY));
        assertFalse(counts(LocalDateTime.of(2025, 10, 16, 0, 0), WiredHighscoreClearType.DAILY));
        assertFalse(counts(LocalDateTime.of(2025, 10, 14, 23, 59, 59), WiredHighscoreClearType.DAILY));
    }

    @Test
    void aWeekRunsFromItsFirstDayUpToTheNextOne() {
        assertTrue(counts(LocalDateTime.of(2025, 10, 13, 0, 0), WiredHighscoreClearType.WEEKLY));
        assertTrue(counts(LocalDateTime.of(2025, 10, 19, 23, 59, 59), WiredHighscoreClearType.WEEKLY));
        assertFalse(counts(LocalDateTime.of(2025, 10, 20, 0, 0), WiredHighscoreClearType.WEEKLY));
        assertFalse(counts(LocalDateTime.of(2025, 10, 12, 23, 59, 59), WiredHighscoreClearType.WEEKLY));
    }

    @Test
    void aWeekStartsOnTheHotelsFirstDayOfWeek() {
        assertTrue(WiredHighscoreManager.isInCurrentPeriod(
                at(LocalDateTime.of(2025, 10, 12, 0, 0)), WiredHighscoreClearType.WEEKLY, NOW, DayOfWeek.SUNDAY));
        assertFalse(WiredHighscoreManager.isInCurrentPeriod(
                at(LocalDateTime.of(2025, 10, 19, 0, 0)), WiredHighscoreClearType.WEEKLY, NOW, DayOfWeek.SUNDAY));
    }

    @Test
    void aMonthRunsFromItsFirstDayUpToTheNextOne() {
        assertTrue(counts(LocalDateTime.of(2025, 10, 1, 0, 0), WiredHighscoreClearType.MONTHLY));
        assertTrue(counts(LocalDateTime.of(2025, 10, 31, 23, 59, 59), WiredHighscoreClearType.MONTHLY));
        assertFalse(counts(LocalDateTime.of(2025, 11, 1, 0, 0), WiredHighscoreClearType.MONTHLY));
        assertFalse(counts(LocalDateTime.of(2025, 9, 30, 23, 59, 59), WiredHighscoreClearType.MONTHLY));
    }

    @Test
    void allTimeCountsEverything() {
        assertTrue(counts(LocalDateTime.of(2001, 1, 1, 0, 0), WiredHighscoreClearType.ALLTIME));
    }
}
