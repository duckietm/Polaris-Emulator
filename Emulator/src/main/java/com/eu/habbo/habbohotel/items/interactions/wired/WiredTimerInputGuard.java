package com.eu.habbo.habbohotel.items.interactions.wired;

public final class WiredTimerInputGuard {
    public static final int MAX_TIMER_MS = 24 * 60 * 60 * 1000;

    private WiredTimerInputGuard() {
    }

    public static int fromClientUnits(int units, int stepMs, int minMs) {
        return fromClientUnits(units, stepMs, minMs, MAX_TIMER_MS);
    }

    public static int fromClientUnits(int units, int stepMs, int minMs, int maxMs) {
        if (units < 1 || stepMs < 1) {
            return minMs;
        }

        long value = (long) units * stepMs;
        return clamp(value, minMs, maxMs);
    }

    public static int normalizeStoredMillis(Integer millis, int minMs, int fallbackMs) {
        return normalizeStoredMillis(millis, minMs, fallbackMs, MAX_TIMER_MS);
    }

    public static int normalizeStoredMillis(Integer millis, int minMs, int fallbackMs, int maxMs) {
        if (millis == null || millis < minMs) {
            return fallbackMs;
        }

        return clamp(millis.longValue(), minMs, maxMs);
    }

    private static int clamp(long value, int minMs, int maxMs) {
        if (value < minMs) {
            return minMs;
        }

        if (value > maxMs) {
            return maxMs;
        }

        return (int) value;
    }
}
