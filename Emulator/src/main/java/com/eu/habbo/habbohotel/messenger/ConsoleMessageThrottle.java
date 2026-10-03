package com.eu.habbo.habbohotel.messenger;

/** Per-user console flood limit, kept apart from room chat so neither eats the other's window. */
public final class ConsoleMessageThrottle {
    public static final long MIN_INTERVAL_MS = 750;

    private long lastMessageAt;

    /** True when the message comes too soon after the last one; otherwise records it. */
    public synchronized boolean flooded(long nowMillis) {
        if (this.lastMessageAt != 0 && nowMillis - this.lastMessageAt < MIN_INTERVAL_MS) return true;

        this.lastMessageAt = nowMillis;
        return false;
    }
}
