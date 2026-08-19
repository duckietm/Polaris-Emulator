package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationalProfileTest {
    @Test
    void customPreservesTheExistingAdaptiveExecutorDefaults() {
        OperationalProfile.Settings settings = OperationalProfile.resolve("custom", 12, 0, 0);

        assertEquals(OperationalProfile.CUSTOM, settings.profile());
        assertEquals(8, settings.persistenceThreads());
        assertEquals(2_048, settings.persistenceQueueCapacity());
    }

    @Test
    void namedProfilesProvideBoundedSizing() {
        OperationalProfile.Settings small = OperationalProfile.resolve("SMALL", 32, 0, 0);
        OperationalProfile.Settings medium = OperationalProfile.resolve("medium", 32, 0, 0);
        OperationalProfile.Settings large = OperationalProfile.resolve("large", 32, 0, 0);

        assertEquals(2, small.persistenceThreads());
        assertEquals(1_024, small.persistenceQueueCapacity());
        assertEquals(4, medium.persistenceThreads());
        assertEquals(2_048, medium.persistenceQueueCapacity());
        assertEquals(8, large.persistenceThreads());
        assertEquals(4_096, large.persistenceQueueCapacity());
    }

    @Test
    void explicitExecutorSizingAlwaysWins() {
        OperationalProfile.Settings settings = OperationalProfile.resolve("small", 4, 6, 8_192);

        assertEquals(6, settings.persistenceThreads());
        assertEquals(8_192, settings.persistenceQueueCapacity());
    }

    @Test
    void rejectsUnknownProfilesAndUnsafeOverrides() {
        assertThrows(IllegalArgumentException.class, () -> OperationalProfile.resolve("huge", 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> OperationalProfile.resolve("custom", 8, 65, 0));
        assertThrows(IllegalArgumentException.class, () -> OperationalProfile.resolve("custom", 8, 0, 65_537));
    }
}
