package com.eu.habbo.habbohotel.rooms;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A room's Show Message budget: Habbo shows at most {@link #LIMIT} wired messages in any
 * {@link #WINDOW_MS} and drops the rest.
 */
public final class WiredMessageBudget {
    public static final int LIMIT = 10;
    public static final long WINDOW_MS = 5000L;

    private final Deque<Long> shown = new ArrayDeque<>();

    /**
     * Takes one message from the budget.
     *
     * @return false when the room already showed {@link #LIMIT} messages in the last {@link #WINDOW_MS}
     */
    public boolean tryAdmit(long now) {
        synchronized (this.shown) {
            while (!this.shown.isEmpty() && now - this.shown.peekFirst() >= WINDOW_MS) {
                this.shown.removeFirst();
            }

            if (this.shown.size() >= LIMIT) {
                return false;
            }

            this.shown.addLast(now);
            return true;
        }
    }
}
