package com.eu.habbo.messages;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides how a handler exception is logged. Every exception used to be logged with its full stack trace, so a client
 * that sends a packet its handler cannot take (outside a room, about a user who left) wrote a stack trace per packet,
 * several a second, and grew the log files. Now the stack trace of one packet and exception type is written once per
 * window; the repeats in between are counted and reported with the next one.
 */
final class PacketErrorLogLimiter {
    /** How many packet and exception pairs are tracked at once; past that the oldest ones are forgotten. */
    static final int MAX_TRACKED = 1024;

    private static final class Entry {
        private long loggedAt;
        private int suppressed;

        private Entry(long loggedAt) {
            this.loggedAt = loggedAt;
        }
    }

    /** What to write for one exception: nothing, or a line with the number of repeats it stands for. */
    record Decision(boolean log, int suppressedSinceLast) {}

    private final long windowMillis;
    private final Map<String, Entry> entries = new HashMap<>();

    PacketErrorLogLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    synchronized Decision record(int header, Class<?> exceptionType, long nowMillis) {
        String key = header + ":" + exceptionType.getName();
        Entry entry = this.entries.get(key);

        if (entry == null) {
            if (this.entries.size() >= MAX_TRACKED) this.entries.clear();
            this.entries.put(key, new Entry(nowMillis));
            return new Decision(true, 0);
        }

        if (nowMillis - entry.loggedAt < this.windowMillis) {
            entry.suppressed++;
            return new Decision(false, entry.suppressed);
        }

        int suppressed = entry.suppressed;
        entry.loggedAt = nowMillis;
        entry.suppressed = 0;
        return new Decision(true, suppressed);
    }
}
