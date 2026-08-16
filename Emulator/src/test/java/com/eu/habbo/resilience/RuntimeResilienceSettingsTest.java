package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;

class RuntimeResilienceSettingsTest {

    @Test
    void capsCircuitMinimumCallsAtTheSlidingWindowSize() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getValue("runtime.resilience.mode", "observe")).thenReturn("observe");
        when(configuration.getInt("runtime.resilience.circuit.window", 20)).thenReturn(2);
        when(configuration.getInt("runtime.resilience.circuit.minimum_calls", 10))
                .thenReturn(10);

        RuntimeResilienceSettings settings = RuntimeResilienceSettings.from(configuration);

        assertEquals(2, settings.circuitBreaker().slidingWindowSize());
        assertEquals(2, settings.circuitBreaker().minimumNumberOfCalls());
    }

    @Test
    void readsEnforcementAndClampsUnsafeValuesToConservativeDefaults() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getValue("runtime.resilience.mode", "observe")).thenReturn("enforce");
        when(configuration.getInt("runtime.resilience.sample.interval_ms", 1000))
                .thenReturn(0);
        when(configuration.getInt("runtime.resilience.degraded.percent", 75)).thenReturn(99);
        when(configuration.getInt("runtime.resilience.critical.percent", 95)).thenReturn(90);
        when(configuration.getInt("runtime.resilience.database.critical_waiters", 2))
                .thenReturn(-1);
        when(configuration.getInt("runtime.resilience.degraded.windows", 3)).thenReturn(0);
        when(configuration.getInt("runtime.resilience.critical.windows", 2)).thenReturn(0);
        when(configuration.getInt("runtime.resilience.recovery.windows", 10)).thenReturn(0);
        when(configuration.getInt("runtime.resilience.scheduler.critical_queue", 10000))
                .thenReturn(0);
        when(configuration.getInt("persistence.executor.queue.capacity", 2048)).thenReturn(0);

        RuntimeResilienceSettings settings = RuntimeResilienceSettings.from(configuration);

        assertEquals(RuntimeResilienceController.Mode.ENFORCE, settings.mode());
        assertEquals(1_000L, settings.sampleIntervalMillis());
        assertEquals(0.75D, settings.degradedUtilization());
        assertEquals(0.95D, settings.criticalUtilization());
        assertEquals(2, settings.criticalDatabaseWaiters());
        assertEquals(3, settings.degradedWindows());
        assertEquals(2, settings.criticalWindows());
        assertEquals(10, settings.recoveryWindows());
        assertEquals(10_000, settings.schedulerCriticalQueue());
        assertEquals(2_048, settings.persistenceQueueCapacity());
    }
}
