package com.eu.habbo.habbohotel.items.interactions.wired.chest;

/**
 * How much a chest may hold, after the official AIR chests: a coin chest starts at 5000 coins and
 * grows by 5000 per upgrade up to 100000; a furni chest starts at 1000 furni and grows by 1000 up to
 * 10000; a starter chest holds 100 furni and is never upgraded.
 *
 * <p>The client computes the same ceiling from its {@code wired.coins_chest.*} and
 * {@code wired.furni_chest.*} configuration, so the two have to agree.
 */
public final class ChestCapacity {
    public static final int COINS_INITIAL = 5000;
    public static final int COINS_UPGRADE = 5000;
    public static final int COINS_MAX_LEVEL = 19;

    public static final int FURNI_INITIAL = 1000;
    public static final int FURNI_UPGRADE = 1000;
    public static final int FURNI_MAX_LEVEL = 9;

    public static final int STARTER = 100;

    private ChestCapacity() {}

    public static int maxLevel(int kind, boolean starter) {
        if (starter) return 0;
        return kind == ChestStorage.KIND_FURNI ? FURNI_MAX_LEVEL : COINS_MAX_LEVEL;
    }

    /** The bought ceiling for a chest of this kind at this capacity level. */
    public static int maxCapacity(int kind, boolean starter, int level) {
        if (starter) return STARTER;

        int clamped = Math.max(0, Math.min(level, maxLevel(kind, false)));
        return kind == ChestStorage.KIND_FURNI
                ? FURNI_INITIAL + FURNI_UPGRADE * clamped
                : COINS_INITIAL + COINS_UPGRADE * clamped;
    }

    /**
     * The smallest level whose ceiling covers {@code needed}, capped at the kind's top level. Used to
     * carry a chest over from the old model without shrinking what its owner paid for.
     */
    public static int levelCovering(int kind, boolean starter, int needed) {
        int max = maxLevel(kind, starter);
        for (int level = 0; level <= max; level++) {
            if (maxCapacity(kind, starter, level) >= needed) return level;
        }
        return max;
    }
}
