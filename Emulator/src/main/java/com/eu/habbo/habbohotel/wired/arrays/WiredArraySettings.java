package com.eu.habbo.habbohotel.wired.arrays;

import java.util.concurrent.atomic.AtomicReference;

public final class WiredArraySettings {
    public static final int DEFAULT_MAX_OWNERS_PER_EXECUTION = 50;
    private static final int ABSOLUTE_MAX_POPULATED_CELLS = 16_384;
    private static final int ABSOLUTE_MAX_OWNERS_PER_EXECUTION = 50;
    private static final AtomicReference<Limits> LIMITS = new AtomicReference<>(Limits.defaults());

    private WiredArraySettings() {}

    public static int maxEntries() {
        return LIMITS.get().maxEntries();
    }

    public static int maxPopulatedCellsPerOwner() {
        return LIMITS.get().maxPopulatedCellsPerOwner();
    }

    public static int maxOwnersPerExecution() {
        return LIMITS.get().maxOwnersPerExecution();
    }

    public static void configure(int maxEntries, int maxPopulatedCellsPerOwner, int maxOwnersPerExecution) {
        LIMITS.set(new Limits(
                clamp(maxEntries, WiredArrayDefinition.ABSOLUTE_MAX_ENTRIES),
                clamp(maxPopulatedCellsPerOwner, ABSOLUTE_MAX_POPULATED_CELLS),
                clamp(maxOwnersPerExecution, ABSOLUTE_MAX_OWNERS_PER_EXECUTION)));
    }

    private static int clamp(int configured, int maximum) {
        return Math.max(1, Math.min(maximum, configured));
    }

    private record Limits(int maxEntries, int maxPopulatedCellsPerOwner, int maxOwnersPerExecution) {
        private static Limits defaults() {
            return new Limits(
                    WiredArrayDefinition.ABSOLUTE_MAX_ENTRIES,
                    WiredArrayDefinition.DEFAULT_MAX_POPULATED_CELLS,
                    DEFAULT_MAX_OWNERS_PER_EXECUTION);
        }
    }
}
