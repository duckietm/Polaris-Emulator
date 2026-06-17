package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

public final class WiredUserActionInputGuard {
    private WiredUserActionInputGuard() {
    }

    public static boolean isRecentTimestamp(long timestamp, long now, long windowMs) {
        if (timestamp < 1 || timestamp > now || windowMs < 1) {
            return false;
        }

        return (now - timestamp) <= windowMs;
    }
}
