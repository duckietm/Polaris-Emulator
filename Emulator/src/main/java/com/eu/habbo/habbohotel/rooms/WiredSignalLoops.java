package com.eu.habbo.habbohotel.rooms;

import java.util.HashMap;
import java.util.Map;

/**
 * AIR's signal loop limit: a receiving stack runs at most {@link #LIMIT} times in a 500 ms wired window, and at
 * most {@link #LIMIT} times in one signal chain (so a loop over a window's end still stops). Signals past it are
 * dropped without a word.
 */
public final class WiredSignalLoops {
    public static final int LIMIT = 100;
    public static final long WINDOW_MS = 500L;

    private final Map<Integer, Integer> runs = new HashMap<>();
    private long window = Long.MIN_VALUE;

    /** Counts one run of the stack {@code stackKey}; false when the window or the chain already ran it 100 times. */
    public synchronized boolean admit(int stackKey, Map<Integer, Integer> chain, long now) {
        long current = Math.floorDiv(now, WINDOW_MS);
        if (current != this.window) {
            this.window = current;
            this.runs.clear();
        }

        int inWindow = this.runs.getOrDefault(stackKey, 0);
        if (inWindow >= LIMIT) {
            return false;
        }

        if (chain != null) {
            synchronized (chain) {
                int inChain = chain.getOrDefault(stackKey, 0);
                if (inChain >= LIMIT) {
                    return false;
                }
                chain.put(stackKey, inChain + 1);
            }
        }

        this.runs.put(stackKey, inWindow + 1);
        return true;
    }
}
