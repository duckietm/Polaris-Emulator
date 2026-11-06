package polaris.hotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnowWarScoreWriterTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 12);

    /** Collects scheduled tasks so the test decides when each one runs. */
    private static final class ManualScheduler implements SnowWarScoreWriter.Scheduler {
        final Deque<Runnable> tasks = new ArrayDeque<>();
        final List<Long> delays = new ArrayList<>();
        boolean accepting = true;

        @Override
        public boolean schedule(Runnable task, long delayMs) {
            if (!this.accepting) {
                return false;
            }
            this.tasks.add(task);
            this.delays.add(delayMs);
            return true;
        }

        void runNext() {
            this.tasks.removeFirst().run();
        }
    }

    private static List<SnowWarLeaderboardRepository.ScoreRow> rows() {
        return List.of(
                new SnowWarLeaderboardRepository.ScoreRow(1, 40), new SnowWarLeaderboardRepository.ScoreRow(2, 15));
    }

    @Test
    void savesInTheBackgroundAndClearsPendingScores() {
        ManualScheduler scheduler = new ManualScheduler();
        List<LocalDate> savedWeeks = new ArrayList<>();
        SnowWarScoreWriter writer = new SnowWarScoreWriter((week, rows) -> savedWeeks.add(week), scheduler);

        writer.submit(WEEK, rows());

        assertTrue(savedWeeks.isEmpty(), "nothing is saved on the caller's thread");
        assertEquals(Map.of(1, 40, 2, 15), writer.pendingScores(List.of(1, 2, 3)));

        scheduler.runNext();

        assertEquals(List.of(WEEK), savedWeeks);
        assertTrue(writer.pendingScores(List.of(1, 2)).isEmpty());
    }

    @Test
    void retriesWithAGrowingDelayUntilTheDatabaseIsBack() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] calls = {0};
        SnowWarScoreWriter writer = new SnowWarScoreWriter((week, rows) -> ++calls[0] >= 3, scheduler);

        writer.submit(WEEK, rows());
        scheduler.runNext(); // fails
        scheduler.runNext(); // fails
        assertEquals(Map.of(1, 40, 2, 15), writer.pendingScores(List.of(1, 2)));
        scheduler.runNext(); // succeeds

        assertEquals(3, calls[0]);
        assertEquals(List.of(0L, 1_000L, 2_000L), scheduler.delays);
        assertTrue(scheduler.tasks.isEmpty());
        assertTrue(writer.pendingScores(List.of(1, 2)).isEmpty());
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] calls = {0};
        SnowWarScoreWriter writer = new SnowWarScoreWriter(
                (week, rows) -> {
                    calls[0]++;
                    throw new IllegalStateException("database down");
                },
                scheduler);

        writer.submit(WEEK, rows());
        while (!scheduler.tasks.isEmpty()) {
            scheduler.runNext();
        }

        assertEquals(SnowWarScoreWriter.MAX_ATTEMPTS, calls[0]);
        assertTrue(writer.pendingScores(List.of(1, 2)).isEmpty());
    }

    @Test
    void savesInlineWhenThePoolNoLongerAcceptsWork() {
        ManualScheduler scheduler = new ManualScheduler();
        scheduler.accepting = false;
        int[] calls = {0};
        SnowWarScoreWriter writer = new SnowWarScoreWriter((week, rows) -> ++calls[0] > 0, scheduler);

        writer.submit(WEEK, rows());

        assertEquals(1, calls[0]);
        assertTrue(writer.pendingScores(List.of(1, 2)).isEmpty());
    }

    @Test
    void pendingScoresOfSeveralMatchesAddUp() {
        ManualScheduler scheduler = new ManualScheduler();
        SnowWarScoreWriter writer = new SnowWarScoreWriter((week, rows) -> true, scheduler);

        writer.submit(WEEK, rows());
        writer.submit(WEEK, List.of(new SnowWarLeaderboardRepository.ScoreRow(1, 10)));
        assertEquals(Map.of(1, 50), writer.pendingScores(List.of(1)));

        scheduler.runNext();
        assertEquals(Map.of(1, 10), writer.pendingScores(List.of(1)));
    }

    @Test
    void retryDelayIsCapped() {
        assertEquals(1_000L, SnowWarScoreWriter.retryDelayMs(1));
        assertEquals(4_000L, SnowWarScoreWriter.retryDelayMs(3));
        assertEquals(SnowWarScoreWriter.MAX_RETRY_MS, SnowWarScoreWriter.retryDelayMs(30));
    }

    @Test
    void idlePlayersAreWarnedOnceThenRemoved() {
        long timeout = 60_000L;
        long warning = 15_000L;
        assertEquals(SnowWarGame.IdleStatus.ACTIVE, SnowWarGame.idleStatus(10_000L, false, timeout, warning));
        assertEquals(SnowWarGame.IdleStatus.WARN, SnowWarGame.idleStatus(45_000L, false, timeout, warning));
        assertEquals(SnowWarGame.IdleStatus.ACTIVE, SnowWarGame.idleStatus(50_000L, true, timeout, warning));
        assertEquals(SnowWarGame.IdleStatus.REMOVE, SnowWarGame.idleStatus(60_000L, true, timeout, warning));
        assertEquals(SnowWarGame.IdleStatus.ACTIVE, SnowWarGame.idleStatus(600_000L, false, 0, warning));
    }
}
