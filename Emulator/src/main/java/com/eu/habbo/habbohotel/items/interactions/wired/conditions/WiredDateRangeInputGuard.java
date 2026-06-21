package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

public final class WiredDateRangeInputGuard {
    private WiredDateRangeInputGuard() {
    }

    public static int[] normalizeRange(int startDate, int endDate) {
        int start = normalizeTimestamp(startDate);
        int end = normalizeTimestamp(endDate);

        if (start > end) {
            return new int[]{0, 0};
        }

        return new int[]{start, end};
    }

    public static int normalizeTimestamp(int value) {
        return Math.max(0, value);
    }
}
