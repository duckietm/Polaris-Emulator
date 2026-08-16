package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eu.habbo.database.PersistenceExecutor;
import org.junit.jupiter.api.Test;

class PersistenceMetricsContractTest {

    @Test
    void snapshotMapsExecutorPressureWithoutDroppingCounters() {
        PersistenceExecutor.Metrics source = new PersistenceExecutor.Metrics(2, 7, 64, 31, 5L, 9_000_000L, false);
        EmulatorStatsService.PersistenceMetrics mapped = EmulatorStatsService.persistenceMetrics(source);

        assertEquals(2, mapped.activeTasks);
        assertEquals(7, mapped.queueDepth);
        assertEquals(64, mapped.queueCapacity);
        assertEquals(31, mapped.highWaterMark);
        assertEquals(5L, mapped.saturationCount);
        assertEquals(9D, mapped.totalSubmissionWaitMs);
        assertFalse(mapped.accepting);
    }
}
