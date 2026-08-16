package com.eu.habbo.stress;

import java.util.Locale;

public enum LoadValidationProfile {
    SMALL(25, 500, 25, 25, 5, 20, 60),
    MEDIUM(100, 2_500, 100, 100, 20, 100, 120),
    LARGE(300, 10_000, 500, 500, 50, 300, 180);

    private final int bots;
    private final int items;
    private final int rollers;
    private final int wiredStacks;
    private final int wiredEventsPerSecond;
    private final int chatPerSecond;
    private final int durationSeconds;

    LoadValidationProfile(
            int bots,
            int items,
            int rollers,
            int wiredStacks,
            int wiredEventsPerSecond,
            int chatPerSecond,
            int durationSeconds) {
        this.bots = bots;
        this.items = items;
        this.rollers = rollers;
        this.wiredStacks = wiredStacks;
        this.wiredEventsPerSecond = wiredEventsPerSecond;
        this.chatPerSecond = chatPerSecond;
        this.durationSeconds = durationSeconds;
    }

    public static LoadValidationProfile parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("profile must be small, medium, or large", exception);
        }
    }

    public StressScenario scenario(int roomId, int itemId, long seed, boolean movement) {
        return new StressScenario(
                roomId,
                this.bots,
                this.items,
                this.rollers,
                this.wiredStacks,
                this.wiredEventsPerSecond,
                itemId,
                this.chatPerSecond,
                this.durationSeconds,
                seed,
                movement);
    }
}
