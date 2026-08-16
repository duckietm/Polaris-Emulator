package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.resilience.DependencyCircuitBreakers;
import com.eu.habbo.resilience.RuntimeResilienceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmulatorStatsResilienceMetricsTest {

    @Test
    void exposesExternalDependencyCircuitSnapshotsAlongsideRuntimeStatus() {
        DependencyCircuitBreakers.Snapshot turnstile = new DependencyCircuitBreakers.Snapshot("OPEN", 10, 6, 2, 2, 8);
        DependencyCircuitBreakers.Snapshot smtp = new DependencyCircuitBreakers.Snapshot("CLOSED", 4, 0, 0, 0, 0);
        EmulatorStatsService.PersistenceMetrics persistence =
                new EmulatorStatsService.PersistenceMetrics(1, 2, 8, 3, 1L, 0.5D, true);
        EmulatorStatsService.PersistenceOperationMetrics persistenceOperations =
                new EmulatorStatsService.PersistenceOperationMetrics(4L, 3L, 1L, 0L, 0L, 2D, 1D, List.of());

        EmulatorStatsService.Snapshot snapshot = new EmulatorStatsService.Snapshot(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                persistence,
                null,
                persistenceOperations,
                null,
                null,
                null,
                (RuntimeResilienceService.Status) null,
                turnstile,
                smtp);

        assertSame(persistence, snapshot.persistence);
        assertSame(persistenceOperations, snapshot.persistenceOperations);
        assertSame(turnstile, snapshot.turnstileCircuit);
        assertSame(smtp, snapshot.smtpCircuit);
    }
}
