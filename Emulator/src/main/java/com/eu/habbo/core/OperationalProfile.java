package com.eu.habbo.core;

import java.util.Locale;

public enum OperationalProfile {
    CUSTOM(0, 0),
    SMALL(2, 1_024),
    MEDIUM(4, 2_048),
    LARGE(8, 4_096);

    private static final int MAX_PERSISTENCE_THREADS = 64;
    private static final int MAX_PERSISTENCE_QUEUE_CAPACITY = 65_536;

    private final int persistenceThreads;
    private final int persistenceQueueCapacity;

    OperationalProfile(int persistenceThreads, int persistenceQueueCapacity) {
        this.persistenceThreads = persistenceThreads;
        this.persistenceQueueCapacity = persistenceQueueCapacity;
    }

    public static Settings resolve(
            String configuredProfile, int runtimeThreads, int threadOverride, int queueCapacityOverride) {
        OperationalProfile profile = parse(configuredProfile);
        validateOverride("persistence.executor.threads", threadOverride, MAX_PERSISTENCE_THREADS);
        validateOverride("db.persistence.queue.capacity", queueCapacityOverride, MAX_PERSISTENCE_QUEUE_CAPACITY);

        int adaptiveThreads = Math.max(2, Math.min(8, runtimeThreads));
        int threads =
                threadOverride > 0 ? threadOverride : profile == CUSTOM ? adaptiveThreads : profile.persistenceThreads;
        int queueCapacity = queueCapacityOverride > 0
                ? queueCapacityOverride
                : profile == CUSTOM ? 2_048 : profile.persistenceQueueCapacity;
        return new Settings(profile, threads, queueCapacity);
    }

    public static OperationalProfile parse(String value) {
        String normalized =
                value == null || value.isBlank() ? "custom" : value.trim().toLowerCase(Locale.ROOT);
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "runtime.operational.profile must be custom, small, medium, or large", exception);
        }
    }

    private static void validateOverride(String name, int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be 0 or between 1 and " + maximum);
        }
    }

    public record Settings(OperationalProfile profile, int persistenceThreads, int persistenceQueueCapacity) {}
}
