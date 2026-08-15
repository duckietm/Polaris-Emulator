package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.database.PersistenceOperationMonitor;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistenceOperationMetricsContractTest {

    @Test
    void mapsPersistenceCountersDurationsAndSafeFailureMetadata() {
        PersistenceOperationMonitor.Snapshot source = new PersistenceOperationMonitor.Snapshot(
                11L,
                7L,
                2L,
                1L,
                1L,
                12_500_000L,
                8_250_000L,
                List.of(new PersistenceOperationMonitor.RecentFailure(
                        42L, "SaveUser", "FAILED", 1_234L, 2_750_000L, "SQLException")));

        EmulatorStatsService.PersistenceOperationMetrics metrics =
                EmulatorStatsService.persistenceOperationMetrics(source);

        assertEquals(11L, metrics.submitted);
        assertEquals(7L, metrics.succeeded);
        assertEquals(2L, metrics.failed);
        assertEquals(1L, metrics.rejected);
        assertEquals(1L, metrics.active);
        assertEquals(12.5D, metrics.totalDurationMs);
        assertEquals(8.25D, metrics.maxDurationMs);
        assertEquals(1, metrics.recentFailures.size());

        EmulatorStatsService.PersistenceFailureRow failure = metrics.recentFailures.getFirst();
        assertEquals(42L, failure.operationId);
        assertEquals("SaveUser", failure.operationType);
        assertEquals("FAILED", failure.outcome);
        assertEquals(1_234L, failure.startedAtEpochMs);
        assertEquals(2.75D, failure.durationMs);
        assertEquals("SQLException", failure.errorType);
    }

    @Test
    void mapsUnavailablePersistenceExecutorToAnEmptySnapshot() {
        EmulatorStatsService.PersistenceOperationMetrics metrics =
                EmulatorStatsService.persistenceOperationMetrics(null);

        assertEquals(0L, metrics.submitted);
        assertEquals(0L, metrics.active);
        assertTrue(metrics.recentFailures.isEmpty());
    }
}
