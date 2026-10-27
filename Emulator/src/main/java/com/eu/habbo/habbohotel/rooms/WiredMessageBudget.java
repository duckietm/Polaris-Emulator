package com.eu.habbo.habbohotel.rooms;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A Show Message budget, as AIR keeps one for each receiving user: at most {@link #LIMIT} wired messages in any
 * {@link #WINDOW_MS} and at most {@link #LONG_LIMIT} in any {@link #LONG_WINDOW_MS}; the rest are dropped and not
 * counted.
 */
public final class WiredMessageBudget {
    public static final int LIMIT = 10;
    public static final long WINDOW_MS = 5000L;
    public static final int LONG_LIMIT = 40;
    public static final long LONG_WINDOW_MS = 30_000L;

    private final Deque<Long> shown = new ArrayDeque<>();

    /**
     * Takes one message from the budget.
     *
     * @return false when {@link #LIMIT} messages were shown in the last {@link #WINDOW_MS} or {@link #LONG_LIMIT} in
     *     the last {@link #LONG_WINDOW_MS}
     */
    public boolean tryAdmit(long now) {
        synchronized (this.shown) {
            while (!this.shown.isEmpty() && now - this.shown.peekFirst() >= LONG_WINDOW_MS) {
                this.shown.removeFirst();
            }

            if (this.shown.size() >= LONG_LIMIT || this.shownSince(now - WINDOW_MS) >= LIMIT) {
                return false;
            }

            this.shown.addLast(now);
            return true;
        }
    }

    private int shownSince(long from) {
        int count = 0;
        Iterator<Long> newestFirst = this.shown.descendingIterator();

        while (newestFirst.hasNext() && newestFirst.next() > from) {
            count++;
        }

        return count;
    }
}
