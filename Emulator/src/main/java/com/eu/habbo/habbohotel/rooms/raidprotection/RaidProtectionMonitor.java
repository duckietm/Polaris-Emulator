package com.eu.habbo.habbohotel.rooms.raidprotection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Scores what is happening in one room over a short sliding window and says when it looks like a
 * raid.
 *
 * <p>The official client only ever transmits a sensitivity of 0, 1 or 2: what those three levels
 * measure is not described anywhere in it, so this is our definition. A raid is a burst of arrivals
 * that starts talking, which is why arrivals and chat feed one shared score instead of two
 * independent rules — a busy room trips neither on its own.
 *
 * <p>The class holds no {@code Room} or {@code Habbo}: it takes user ids and message text so it can
 * be unit tested directly. Deciding which arrivals count (a friend of the owner does not) belongs
 * to the caller.
 */
public final class RaidProtectionMonitor {
    /** How far back the score looks, in seconds. */
    public static final int WINDOW_SECONDS = 30;

    /** How long after arriving a user's messages still count as a newcomer's, in seconds. */
    public static final int RECENT_ARRIVAL_SECONDS = 60;

    static final int SCORE_ARRIVAL = 1;

    static final int SCORE_NEWCOMER_MESSAGE = 2;

    static final int SCORE_ECHOED_MESSAGE = 3;

    private static final int THRESHOLD_LOW = 25;

    private static final int THRESHOLD_MEDIUM = 15;

    private static final int THRESHOLD_HIGH = 8;

    private final Deque<ScoredEvent> events = new ArrayDeque<>();

    /** Arrival times, so a message can be told from a newcomer's. Pruned with the window. */
    private final java.util.Map<Integer, Long> arrivals = new java.util.HashMap<>();

    /** The score a room must reach at the given sensitivity for an incident to fire. */
    public static int thresholdFor(int sensitivity) {
        return switch (sensitivity) {
            case RaidProtectionSettings.SENSITIVITY_LOW -> THRESHOLD_LOW;
            case RaidProtectionSettings.SENSITIVITY_HIGH -> THRESHOLD_HIGH;
            default -> THRESHOLD_MEDIUM;
        };
    }

    /**
     * Records a user entering the room. The caller passes only arrivals that should count: someone
     * with rights in the room, or a friend of the owner, is not part of a raid.
     */
    public void recordArrival(int userId, long nowSeconds) {
        this.prune(nowSeconds);
        this.arrivals.put(userId, nowSeconds);
        this.events.addLast(new ScoredEvent(userId, nowSeconds, SCORE_ARRIVAL));
    }

    /**
     * Records a chat message. It scores only when it comes from someone who arrived within the last
     * {@link #RECENT_ARRIVAL_SECONDS}, and scores higher when a different user already said the
     * same thing inside the window — the signature of a coordinated flood.
     */
    public void recordMessage(int userId, String message, long nowSeconds) {
        this.prune(nowSeconds);

        Long arrivedAt = this.arrivals.get(userId);

        if (arrivedAt == null || (nowSeconds - arrivedAt) > RECENT_ARRIVAL_SECONDS) {
            return;
        }

        String normalized = normalize(message);
        int score = SCORE_NEWCOMER_MESSAGE;

        if (!normalized.isEmpty() && this.isEchoOfAnotherUser(userId, normalized)) {
            score += SCORE_ECHOED_MESSAGE;
        }

        this.events.addLast(new ScoredEvent(userId, nowSeconds, score, normalized));
    }

    /**
     * The score currently inside the window. Exposed for diagnostics and tests; the decision itself
     * is {@link #evaluate(int, long)}.
     */
    public int currentScore(long nowSeconds) {
        this.prune(nowSeconds);

        int total = 0;

        for (ScoredEvent event : this.events) {
            total += event.score();
        }

        return total;
    }

    /**
     * Returns the users to act on when the window has reached the threshold for this sensitivity,
     * or {@code null} when it has not.
     *
     * <p>Firing clears the window, so one burst produces one incident rather than an incident on
     * every message that follows it.
     */
    public Set<Integer> evaluate(int sensitivity, long nowSeconds) {
        if (this.currentScore(nowSeconds) < thresholdFor(sensitivity)) {
            return null;
        }

        Set<Integer> contributors = new LinkedHashSet<>();

        for (ScoredEvent event : this.events) {
            contributors.add(event.userId());
        }

        this.events.clear();

        return contributors;
    }

    /** Forgets everything, for a room that is closing or has just had its protection turned off. */
    public void reset() {
        this.events.clear();
        this.arrivals.clear();
    }

    private boolean isEchoOfAnotherUser(int userId, String normalized) {
        for (ScoredEvent event : this.events) {
            if (event.userId() != userId && normalized.equals(event.message())) {
                return true;
            }
        }

        return false;
    }

    private void prune(long nowSeconds) {
        long cutoff = nowSeconds - WINDOW_SECONDS;

        while (!this.events.isEmpty() && this.events.peekFirst().atSeconds() < cutoff) {
            this.events.removeFirst();
        }

        this.arrivals.entrySet().removeIf(entry -> entry.getValue() < (nowSeconds - RECENT_ARRIVAL_SECONDS));
    }

    private static String normalize(String message) {
        return (message == null) ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredEvent(int userId, long atSeconds, int score, String message) {
        ScoredEvent(int userId, long atSeconds, int score) {
            this(userId, atSeconds, score, "");
        }
    }
}
