package com.eu.habbo.stress;

import com.eu.habbo.core.ConfigurationManager;

public record StressLimits(
        int maxBots,
        int maxItems,
        int maxRollers,
        int maxWiredStacks,
        int maxWiredEventsPerSecond,
        int maxTotalEntities,
        int maxChatPerSecond,
        int maxDurationSeconds) {
    public static StressLimits configured(ConfigurationManager configuration) {
        return new StressLimits(
                positive(configuration, "stress.max_bots", 5_000),
                positive(configuration, "stress.max_items", 100_000),
                positive(configuration, "stress.max_rollers", 50_000),
                positive(configuration, "stress.max_wired_stacks", 50_000),
                positive(configuration, "stress.max_wired_events_per_second", 100),
                positive(configuration, "stress.max_total_entities", 200_000),
                positive(configuration, "stress.max_chat_per_second", 10_000),
                positive(configuration, "stress.max_duration_seconds", 3_600));
    }

    private static int positive(ConfigurationManager configuration, String key, int fallback) {
        int value = configuration.getInt(key, fallback);
        return value > 0 ? value : fallback;
    }
}
