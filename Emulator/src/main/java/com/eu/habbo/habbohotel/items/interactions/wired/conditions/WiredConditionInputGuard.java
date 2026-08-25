package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

public final class WiredConditionInputGuard {
    public static final int MAX_USER_COUNT_LIMIT = 1000;
    public static final int MAX_TIMER_CYCLES = 24 * 60 * 60 * 2;

    private WiredConditionInputGuard() {
    }

    public static GameTeamColors normalizeTeamColor(GameTeamColors value, GameTeamColors fallback) {
        return (value != null) ? value : fallback;
    }

    public static GameTeamColors normalizeTeamColorType(int value, GameTeamColors fallback) {
        for (GameTeamColors color : GameTeamColors.values()) {
            if (color.type == value) {
                return color;
            }
        }

        return fallback;
    }

    public static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static int normalizeTimerCycles(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_TIMER_CYCLES);
    }

    public static int[] normalizeUserCountRange(int lowerLimit, int upperLimit) {
        int lower = clampUserCount(lowerLimit);
        int upper = clampUserCount(upperLimit);

        if (lower > upper) {
            return new int[]{upper, lower};
        }

        return new int[]{lower, upper};
    }

    private static int clampUserCount(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_USER_COUNT_LIMIT);
    }
}
