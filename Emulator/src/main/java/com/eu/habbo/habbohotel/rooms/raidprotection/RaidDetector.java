package com.eu.habbo.habbohotel.rooms.raidprotection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The in-memory raid detector of one room. A raid is many unknown visitors arriving at once (distinct
 * suspicious entries within {@link #ENTRY_WINDOW_MS}) or a flood of chat from them (lines of
 * newcomers within {@link #CHAT_WINDOW_MS}); the sensitivity picks the threshold. Once detected the
 * incident lasts until the room has been quiet for the hotel's quiet time. Everything is bounded and
 * pruned on the events themselves: no timer, no database.
 */
public final class RaidDetector {
    static final long ENTRY_WINDOW_MS = 60_000L;
    /** Distinct suspicious entrants within the entry window, by sensitivity (low, medium, high). */
    static final int[] ENTRY_THRESHOLDS = {20, 12, 6};

    static final long CHAT_WINDOW_MS = 10_000L;
    /** Chat lines of newcomers within the chat window, by sensitivity (low, medium, high). */
    static final int[] CHAT_THRESHOLDS = {30, 20, 12};
    /** A chat flood needs at least this many different newcomers; one spammer is the flood limit's job. */
    static final int CHAT_MIN_SPEAKERS = 3;

    /** How long after a suspicious entry the visitor's chat still counts. */
    static final long NEWCOMER_MS = 300_000L;

    static final int MAX_EVENTS = 256;
    static final int MAX_TRACKED_USERS = 512;

    public enum Kind {
        /** Nothing to do. */
        NONE,
        /** A raid was detected just now; act on every target. */
        STARTED,
        /** A raid is going on; act on the target, the visitor who just entered or spoke. */
        CONTINUING
    }

    public record Result(Kind kind, List<Integer> targets, String trigger) {
        static final Result NONE = new Result(Kind.NONE, List.of(), "");
    }

    /** One finished incident, to be written to {@code room_raid_incidents}. */
    public record Summary(long startedMs, long endedMs, String trigger, int participants, int actioned, int blocked) {}

    private final ArrayDeque<long[]> entries = new ArrayDeque<>();
    private final ArrayDeque<long[]> chats = new ArrayDeque<>();
    private final Map<Integer, Long> newcomers = new HashMap<>();
    private final Map<Integer, Long> guardBlocks = new HashMap<>();
    private final Set<Integer> participants = new HashSet<>();

    private long incidentStartedMs;
    private long lastActivityMs;
    private String trigger = "";
    private int actioned;
    private int blocked;

    /** A suspicious visitor (see {@link RaidSuspicion#counts}) entered the room. */
    public synchronized Result onEntry(int userId, int sensitivity, long nowMs, long quietMs) {
        prune(nowMs);
        this.newcomers.put(userId, nowMs);
        add(this.entries, nowMs, userId);

        if (this.isIncidentActive(nowMs, quietMs)) {
            return this.continueWith(userId, nowMs);
        }

        if (distinct(this.entries).size() >= threshold(ENTRY_THRESHOLDS, sensitivity)) {
            return this.start("entry", nowMs);
        }
        return Result.NONE;
    }

    /** Someone spoke in the room; only a recent suspicious entrant's line counts. */
    public synchronized Result onChat(int userId, int sensitivity, long nowMs, long quietMs) {
        Long entered = this.newcomers.get(userId);
        if (entered == null || nowMs - entered > NEWCOMER_MS) return Result.NONE;

        prune(nowMs);
        add(this.chats, nowMs, userId);

        if (this.isIncidentActive(nowMs, quietMs)) {
            return this.continueWith(userId, nowMs);
        }

        if (this.chats.size() >= threshold(CHAT_THRESHOLDS, sensitivity)
                && distinct(this.chats).size() >= CHAT_MIN_SPEAKERS) {
            return this.start("chat", nowMs);
        }
        return Result.NONE;
    }

    /** Whether the visitor took part in the incident going on (entered or spoke as a raider). */
    public synchronized boolean isParticipant(int userId) {
        return this.incidentStartedMs > 0 && this.participants.contains(userId);
    }

    public synchronized boolean isIncidentActive(long nowMs, long quietMs) {
        return this.incidentStartedMs > 0 && nowMs - this.lastActivityMs < quietMs;
    }

    /** When the last incident is over, forgets it and returns what happened; null otherwise. */
    public synchronized Summary closeIfQuiet(long nowMs, long quietMs) {
        if (this.incidentStartedMs <= 0 || this.isIncidentActive(nowMs, quietMs)) return null;
        return this.close(this.lastActivityMs + quietMs);
    }

    /** Ends the incident now (the room unloads); null when none is going on. */
    public synchronized Summary closeNow(long nowMs) {
        if (this.incidentStartedMs <= 0) return null;
        return this.close(nowMs);
    }

    public synchronized void recordAction() {
        this.actioned++;
    }

    public synchronized void recordBlocked() {
        this.blocked++;
    }

    /** Keeps a visitor out from {@code nowMs} until {@code untilMs} (the guard duration). */
    public synchronized void block(int userId, long nowMs, long untilMs) {
        if (this.guardBlocks.size() >= MAX_TRACKED_USERS) {
            this.guardBlocks.values().removeIf(until -> until <= nowMs);
            if (this.guardBlocks.size() >= MAX_TRACKED_USERS) return;
        }
        this.guardBlocks.merge(userId, untilMs, Math::max);
    }

    public synchronized boolean isBlocked(int userId, long nowMs) {
        Long until = this.guardBlocks.get(userId);
        if (until == null) return false;
        if (until <= nowMs) {
            this.guardBlocks.remove(userId);
            return false;
        }
        return true;
    }

    private Result continueWith(int userId, long nowMs) {
        this.lastActivityMs = nowMs;
        this.participants.add(userId);
        return new Result(Kind.CONTINUING, List.of(userId), this.trigger);
    }

    private Result start(String trigger, long nowMs) {
        this.incidentStartedMs = nowMs;
        this.lastActivityMs = nowMs;
        this.trigger = trigger;
        this.actioned = 0;
        this.blocked = 0;
        Set<Integer> targets = new LinkedHashSet<>(distinct(this.entries));
        targets.addAll(distinct(this.chats));
        this.participants.clear();
        this.participants.addAll(targets);
        return new Result(Kind.STARTED, List.copyOf(targets), trigger);
    }

    private Summary close(long endedMs) {
        Summary summary = new Summary(
                this.incidentStartedMs, endedMs, this.trigger, this.participants.size(), this.actioned, this.blocked);
        this.incidentStartedMs = 0;
        this.lastActivityMs = 0;
        this.trigger = "";
        this.actioned = 0;
        this.blocked = 0;
        this.participants.clear();
        this.entries.clear();
        this.chats.clear();
        return summary;
    }

    private void prune(long nowMs) {
        while (!this.entries.isEmpty() && nowMs - this.entries.peekFirst()[0] > ENTRY_WINDOW_MS) {
            this.entries.pollFirst();
        }
        while (!this.chats.isEmpty() && nowMs - this.chats.peekFirst()[0] > CHAT_WINDOW_MS) {
            this.chats.pollFirst();
        }
        if (this.newcomers.size() >= MAX_TRACKED_USERS) {
            Iterator<Long> iterator = this.newcomers.values().iterator();
            while (iterator.hasNext()) {
                if (nowMs - iterator.next() > NEWCOMER_MS) iterator.remove();
            }
            if (this.newcomers.size() >= MAX_TRACKED_USERS) this.newcomers.clear();
        }
    }

    private static void add(ArrayDeque<long[]> events, long nowMs, int userId) {
        if (events.size() >= MAX_EVENTS) events.pollFirst();
        events.addLast(new long[] {nowMs, userId});
    }

    private static List<Integer> distinct(ArrayDeque<long[]> events) {
        Set<Integer> users = new LinkedHashSet<>();
        for (long[] event : events) users.add((int) event[1]);
        return new ArrayList<>(users);
    }

    private static int threshold(int[] thresholds, int sensitivity) {
        return thresholds[Math.max(0, Math.min(thresholds.length - 1, sensitivity))];
    }
}
