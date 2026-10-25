package com.eu.habbo.messages.incoming.camera;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * The camera cooldown and the daily photo count, per user id and outside the session. Both lived in the session
 * cache, which every login empties: with a relog every 50 photos one account could write thousands of photos a day,
 * two PNG files each, and fill the server's disk.
 */
final class CameraQuota {

    static final int DAY_SECONDS = 86_400;

    private record Day(int start, int count) {}

    private static final Cache<Integer, Integer> LAST_ATTEMPT = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    private static final Cache<Integer, Day> DAILY = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(DAY_SECONDS + 3_600L))
            .build();

    private CameraQuota() {}

    /** The time of the user's last photo attempt, or null. */
    static Integer lastAttempt(int userId) {
        return LAST_ATTEMPT.getIfPresent(userId);
    }

    static void markAttempt(int userId, int now) {
        LAST_ATTEMPT.put(userId, now);
    }

    /** Gives the cooldown back after a refusal that was the server's doing, not the upload's. */
    static void refundAttempt(int userId) {
        LAST_ATTEMPT.invalidate(userId);
    }

    /** Photos taken in the current 24 h window. */
    static int dailyCount(int userId, int now) {
        Day day = DAILY.getIfPresent(userId);

        return day == null || now - day.start() >= DAY_SECONDS ? 0 : day.count();
    }

    static void recordPhoto(int userId, int now) {
        DAILY.asMap()
                .compute(
                        userId,
                        (id, day) -> day == null || now - day.start() >= DAY_SECONDS
                                ? new Day(now, 1)
                                : new Day(day.start(), day.count() + 1));
    }

    /** Visible for tests. */
    static void reset() {
        LAST_ATTEMPT.invalidateAll();
        DAILY.invalidateAll();
    }
}
