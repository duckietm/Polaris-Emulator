package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimePressureAssessorTest {

    private final RuntimePressureAssessor assessor =
            new RuntimePressureAssessor(new RuntimePressureAssessor.Thresholds(0.75D, 0.95D, 2));

    @Test
    void reportsTheWorstPressureAcrossRuntimeSignals() {
        assertEquals(
                RuntimeResilienceController.Pressure.DEGRADED,
                assessor.assess(sample(0.20D, 0, 0.80D, 0.10D, 0.20D, 0.30D, false)));

        assertEquals(
                RuntimeResilienceController.Pressure.CRITICAL,
                assessor.assess(sample(0.20D, 0, 0.10D, 0.96D, 0.20D, 0.30D, false)));
    }

    @Test
    void databaseWaitersEscalateBeforeThePoolBecomesCompletelyUnavailable() {
        assertEquals(
                RuntimeResilienceController.Pressure.DEGRADED,
                assessor.assess(sample(0.50D, 1, 0.10D, 0.10D, 0.10D, 0.10D, false)));
        assertEquals(
                RuntimeResilienceController.Pressure.CRITICAL,
                assessor.assess(sample(0.50D, 2, 0.10D, 0.10D, 0.10D, 0.10D, false)));
    }

    @Test
    void unavailableCriticalDependencyIsAlwaysCritical() {
        assertEquals(
                RuntimeResilienceController.Pressure.CRITICAL, assessor.assess(sample(0D, 0, 0D, 0D, 0D, 0D, true)));
    }

    @Test
    void healthySignalsRemainHealthy() {
        assertEquals(
                RuntimeResilienceController.Pressure.HEALTHY,
                assessor.assess(sample(0.30D, 0, 0.40D, 0.20D, 0.10D, 0.50D, false)));
    }

    @Test
    void rejectsInvalidRatiosInsteadOfSilentlyNormalizingBadTelemetry() {
        assertThrows(IllegalArgumentException.class, () -> sample(1.01D, 0, 0D, 0D, 0D, 0D, false));
        assertThrows(IllegalArgumentException.class, () -> sample(0D, -1, 0D, 0D, 0D, 0D, false));
    }

    private static RuntimePressureAssessor.Sample sample(
            double database,
            int databaseWaiters,
            double persistence,
            double packets,
            double scheduler,
            double memory,
            boolean criticalDependencyUnavailable) {
        return new RuntimePressureAssessor.Sample(
                database, databaseWaiters, persistence, packets, scheduler, memory, criticalDependencyUnavailable);
    }
}
