package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;

class PolarisBootstrapRuntimeThreadsTest {

    @Test
    void absentRuntimeThreadSettingUsesDefensiveDefault() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getInt("runtime.threads", 8)).thenReturn(0);

        assertEquals(8, PolarisBootstrap.resolveRuntimeThreads(configuration));
    }

    @Test
    void positiveRuntimeThreadSettingRemainsAuthoritative() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getInt("runtime.threads", 8)).thenReturn(12);

        assertEquals(12, PolarisBootstrap.resolveRuntimeThreads(configuration));
    }

    @Test
    void invalidPersistenceQueueCapacityUsesProfileDefault() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getInt("db.persistence.queue.capacity", 0)).thenReturn(-1);

        assertEquals(0, PolarisBootstrap.resolvePersistenceQueueCapacityOverride(configuration));

        when(configuration.getInt("db.persistence.queue.capacity", 0)).thenReturn(65_537);

        assertEquals(0, PolarisBootstrap.resolvePersistenceQueueCapacityOverride(configuration));
    }

    @Test
    void automaticAndExplicitPersistenceQueueCapacityRemainAuthoritative() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getInt("db.persistence.queue.capacity", 0)).thenReturn(0);

        assertEquals(0, PolarisBootstrap.resolvePersistenceQueueCapacityOverride(configuration));

        when(configuration.getInt("db.persistence.queue.capacity", 0)).thenReturn(4_096);

        assertEquals(4_096, PolarisBootstrap.resolvePersistenceQueueCapacityOverride(configuration));
    }
}
