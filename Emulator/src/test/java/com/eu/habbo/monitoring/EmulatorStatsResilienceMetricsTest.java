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

        EmulatorStatsService.Snapshot snapshot = new EmulatorStatsService.Snapshot(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                (RuntimeResilienceService.Status) null,
                turnstile,
                smtp);

        assertSame(turnstile, snapshot.turnstileCircuit);
        assertSame(smtp, snapshot.smtpCircuit);
    }
}
