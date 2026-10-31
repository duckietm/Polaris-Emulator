package com.eu.habbo.habbohotel.wired.highscores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** The highscore table only grew: compaction drops the rows no board and no condition can read any more. */
class WiredHighscoreCompactionTest {

    private static final int NOW = 1_000;

    private static WiredHighscoreDataEntry row(int score, int timestamp, Integer... userIds) {
        return new WiredHighscoreDataEntry(7, List.of(userIds), score, true, timestamp);
    }

    /** Rows at NOW or later count in the current period. */
    private static final Predicate<WiredHighscoreDataEntry> CURRENT = entry -> entry.getTimestamp() >= NOW;

    @Test
    void aDailyBoardKeepsTodayWholeAndEachTeamsBestAndWorstBefore() {
        WiredHighscoreDataEntry today = row(1, NOW + 5, 1);
        WiredHighscoreDataEntry oldBest = row(90, 10, 1);
        WiredHighscoreDataEntry oldMiddle = row(50, 20, 1);
        WiredHighscoreDataEntry oldWorst = row(5, 30, 1);
        WiredHighscoreDataEntry otherTeam = row(40, 40, 2, 3);

        List<WiredHighscoreDataEntry> kept = WiredHighscoreManager.compacted(
                List.of(today, oldBest, oldMiddle, oldWorst, otherTeam),
                WiredHighscoreClearType.DAILY,
                WiredHighscoreScoreType.CLASSIC,
                CURRENT);

        assertEquals(List.of(today, oldBest, oldWorst, otherTeam), kept);
    }

    @Test
    void aTeamIsTheSetOfItsUsersWhateverTheirOrder() {
        WiredHighscoreDataEntry best = row(80, 10, 2, 3);
        WiredHighscoreDataEntry middle = row(60, 20, 3, 2);
        WiredHighscoreDataEntry worst = row(10, 30, 3, 2);

        List<WiredHighscoreDataEntry> kept = WiredHighscoreManager.compacted(
                List.of(best, middle, worst), WiredHighscoreClearType.WEEKLY, WiredHighscoreScoreType.PERTEAM, CURRENT);

        assertEquals(List.of(best, worst), kept);
    }

    @Test
    void allTimeClassicAndMostWinsBoardsAreLeftWhole() {
        List<WiredHighscoreDataEntry> rows = List.of(row(5, 10, 1), row(6, 20, 1), row(7, 30, 1));

        assertEquals(
                rows,
                WiredHighscoreManager.compacted(
                        rows, WiredHighscoreClearType.ALLTIME, WiredHighscoreScoreType.CLASSIC, CURRENT));
        assertEquals(
                rows,
                WiredHighscoreManager.compacted(
                        rows, WiredHighscoreClearType.ALLTIME, WiredHighscoreScoreType.MOSTWIN, CURRENT));
    }

    @Test
    void anAllTimeFastestBoardKeepsEachTeamsFastestTime() {
        WiredHighscoreDataEntry slow = row(300, NOW + 1, 1);
        WiredHighscoreDataEntry fast = row(120, NOW + 2, 1);
        WiredHighscoreDataEntry middle = row(200, NOW + 3, 1);

        List<WiredHighscoreDataEntry> kept = WiredHighscoreManager.compacted(
                List.of(slow, fast, middle),
                WiredHighscoreClearType.ALLTIME,
                WiredHighscoreScoreType.FASTESTTIME,
                CURRENT);

        assertEquals(2, kept.size());
        assertSame(slow, kept.get(0));
        assertSame(fast, kept.get(1));
    }
}
