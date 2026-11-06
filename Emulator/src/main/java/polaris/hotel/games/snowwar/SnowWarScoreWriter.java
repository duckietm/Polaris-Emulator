package polaris.hotel.games.snowwar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves finished matches off the game loop and retries with a growing delay,
 * so a slow or briefly unavailable database never stalls the end of a match.
 *
 * <p>Scores that are not committed yet are kept in memory and added to the
 * totals the results screen and lobby read, so a quick rematch already shows
 * the right skill level.
 */
final class SnowWarScoreWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarScoreWriter.class);

    static final int MAX_ATTEMPTS = 8;
    static final long FIRST_RETRY_MS = 1_000L;
    static final long MAX_RETRY_MS = 60_000L;

    /** Runs a task after a delay; returns {@code false} when it was not accepted (shutting down). */
    @FunctionalInterface
    interface Scheduler {
        boolean schedule(Runnable task, long delayMs);
    }

    @FunctionalInterface
    interface Store {
        boolean save(LocalDate weekStart, List<SnowWarLeaderboardRepository.ScoreRow> rows);
    }

    private final Store store;
    private final Scheduler scheduler;
    private final Map<Integer, Integer> pendingScores = new ConcurrentHashMap<>();

    SnowWarScoreWriter(Store store, Scheduler scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    /** Queues a finished match. The week is fixed now, so a retry after Monday 00:00 UTC still counts for it. */
    void submit(LocalDate weekStart, List<SnowWarLeaderboardRepository.ScoreRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<SnowWarLeaderboardRepository.ScoreRow> copy = List.copyOf(rows);
        for (SnowWarLeaderboardRepository.ScoreRow row : copy) {
            this.pendingScores.merge(row.userId(), Math.max(0, row.score()), Integer::sum);
        }
        this.schedule(weekStart, copy, 1, 0L);
    }

    /** Scores of finished matches that are not in the database yet, per user. */
    Map<Integer, Integer> pendingScores(Collection<Integer> userIds) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Integer userId : userIds) {
            Integer pending = this.pendingScores.get(userId);
            if (pending != null && pending > 0) {
                result.put(userId, pending);
            }
        }
        return result;
    }

    static long retryDelayMs(int failedAttempt) {
        long delay = FIRST_RETRY_MS << Math.min(16, Math.max(0, failedAttempt - 1));
        return Math.min(MAX_RETRY_MS, delay);
    }

    private void schedule(
            LocalDate weekStart, List<SnowWarLeaderboardRepository.ScoreRow> rows, int attempt, long delayMs) {
        boolean accepted;
        try {
            accepted = this.scheduler.schedule(() -> this.attempt(weekStart, rows, attempt), delayMs);
        } catch (RuntimeException exception) {
            accepted = false;
        }
        if (!accepted) {
            // The thread pool no longer takes work (hotel shutting down):
            // make one last attempt right here rather than drop the match.
            this.attempt(weekStart, rows, MAX_ATTEMPTS);
        }
    }

    private void attempt(LocalDate weekStart, List<SnowWarLeaderboardRepository.ScoreRow> rows, int attempt) {
        boolean saved;
        try {
            saved = this.store.save(weekStart, rows);
        } catch (RuntimeException exception) {
            LOGGER.warn("SnowWar score save attempt {} threw.", attempt, exception);
            saved = false;
        }

        if (saved) {
            this.release(rows);
            if (attempt > 1) {
                LOGGER.info("SnowWar scores for {} player(s) saved on attempt {}.", rows.size(), attempt);
            }
            return;
        }

        if (attempt >= MAX_ATTEMPTS) {
            this.release(rows);
            LOGGER.error("SnowWar scores could not be saved after {} attempts (week {}): {}", attempt, weekStart, rows);
            return;
        }

        long delay = retryDelayMs(attempt);
        LOGGER.warn("SnowWar score save attempt {} failed; retrying in {} MS.", attempt, delay);
        this.schedule(weekStart, rows, attempt + 1, delay);
    }

    private void release(List<SnowWarLeaderboardRepository.ScoreRow> rows) {
        for (SnowWarLeaderboardRepository.ScoreRow row : rows) {
            int score = Math.max(0, row.score());
            this.pendingScores.computeIfPresent(row.userId(), (id, pending) -> {
                int left = pending - score;
                return left > 0 ? left : null;
            });
        }
    }
}
