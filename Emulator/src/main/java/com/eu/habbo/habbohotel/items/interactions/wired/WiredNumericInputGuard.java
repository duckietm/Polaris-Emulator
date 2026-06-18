package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.Emulator;

public final class WiredNumericInputGuard {
    public static final int DEFAULT_MAX_REWARD_AMOUNT = 1000;
    public static final int DEFAULT_MAX_RESPECT_AMOUNT = 100;
    public static final int MAX_ABSOLUTE_AMOUNT = 100000;

    private WiredNumericInputGuard() {
    }

    public static int parsePositiveAmount(String value, int maxAmount) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed <= 0) {
                return 0;
            }

            return Math.min(parsed, Math.max(1, Math.min(maxAmount, MAX_ABSOLUTE_AMOUNT)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int maxRewardAmount() {
        return configuredMax("hotel.wired.reward.max_amount", DEFAULT_MAX_REWARD_AMOUNT);
    }

    public static int maxRespectAmount() {
        return configuredMax("hotel.wired.respect.max_amount", DEFAULT_MAX_RESPECT_AMOUNT);
    }

    private static int configuredMax(String key, int fallback) {
        int configured = Emulator.getConfig() != null ? Emulator.getConfig().getInt(key, fallback) : fallback;
        return Math.max(1, Math.min(configured, MAX_ABSOLUTE_AMOUNT));
    }
}
