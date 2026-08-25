package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

public final class WiredUserConditionInputGuard {
    public static final int MAX_BADGE_CODE_LENGTH = 64;
    public static final int MAX_EFFECT_ID = 10_000;
    public static final int MAX_HAND_ITEM_ID = 10_000;

    private WiredUserConditionInputGuard() {
    }

    public static String normalizeBadgeCode(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (normalized.length() > MAX_BADGE_CODE_LENGTH) {
            return normalized.substring(0, MAX_BADGE_CODE_LENGTH);
        }

        return normalized;
    }

    public static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static int normalizeEffectId(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_EFFECT_ID);
    }

    public static int normalizeHandItemId(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_HAND_ITEM_ID);
    }
}
