package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimePressureSamplerTest {

    @Test
    void convertsAbsoluteRuntimeReadingsIntoBoundedUtilizationRatios() {
        RuntimePressureSampler sampler = new RuntimePressureSampler(
                () -> new RuntimePressureSampler.DatabaseReading(8, 10, 3, false),
                () -> new RuntimePressureSampler.QueueReading(1536, 2048),
                () -> new RuntimePressureSampler.QueueReading(192, 256),
                () -> new RuntimePressureSampler.QueueReading(7500, 10000),
                () -> 0.90D);

        RuntimePressureAssessor.Sample sample = sampler.sample();

        assertEquals(0.80D, sample.databaseUtilization());
        assertEquals(3, sample.databaseWaiters());
        assertEquals(0.75D, sample.persistenceQueueUtilization());
        assertEquals(0.75D, sample.packetQueueUtilization());
        assertEquals(0.75D, sample.schedulerQueueUtilization());
        assertEquals(0.90D, sample.memoryUtilization());
    }

    @Test
    void unavailableDatabaseIsReportedAsCriticalWithoutInvalidRatios() {
        RuntimePressureSampler sampler = new RuntimePressureSampler(
                () -> new RuntimePressureSampler.DatabaseReading(0, 0, 0, true),
                () -> new RuntimePressureSampler.QueueReading(0, 0),
                () -> new RuntimePressureSampler.QueueReading(0, 0),
                () -> new RuntimePressureSampler.QueueReading(0, 0),
                () -> 0D);

        RuntimePressureAssessor.Sample sample = sampler.sample();

        assertEquals(0D, sample.databaseUtilization());
        assertEquals(true, sample.criticalDependencyUnavailable());
    }
}
