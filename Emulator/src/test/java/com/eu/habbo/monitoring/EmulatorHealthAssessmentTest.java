package com.eu.habbo.monitoring;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorHealthAssessmentTest {

    @Test
    void reportsHealthyOnlyWhenEveryComponentIsHealthy() {
        EmulatorStatsService.HealthSnapshot health = EmulatorStatsService.assessHealth(
                1_234L,
                List.of(
                        EmulatorStatsService.HealthCheck.healthy("database", true, "pool ready"),
                        EmulatorStatsService.HealthCheck.healthy("tcp", true, "listener active"),
                        EmulatorStatsService.HealthCheck.healthy("websocket", false, "disabled")
                )
        );

        assertEquals(EmulatorStatsService.HealthStatus.HEALTHY, health.status);
        assertEquals(1_234L, health.checkedAtEpochMs);
        assertTrue(health.reasons.isEmpty());
        assertEquals(3, health.checks.size());
    }

    @Test
    void reportsUnhealthyWhenACriticalComponentIsDown() {
        EmulatorStatsService.HealthSnapshot health = EmulatorStatsService.assessHealth(
                2_000L,
                List.of(
                        EmulatorStatsService.HealthCheck.healthy("runtime", true, "ready"),
                        EmulatorStatsService.HealthCheck.unhealthy("database", true, "pool closed")
                )
        );

        assertEquals(EmulatorStatsService.HealthStatus.UNHEALTHY, health.status);
        assertEquals(List.of("database: pool closed"), health.reasons);
    }

    @Test
    void reportsDegradedForWarningsOrNonCriticalFailures() {
        EmulatorStatsService.HealthSnapshot health = EmulatorStatsService.assessHealth(
                3_000L,
                List.of(
                        EmulatorStatsService.HealthCheck.degraded("jvm", true, "memory pressure"),
                        EmulatorStatsService.HealthCheck.unhealthy("websocket", false, "listener unavailable")
                )
        );

        assertEquals(EmulatorStatsService.HealthStatus.DEGRADED, health.status);
        assertEquals(List.of("jvm: memory pressure", "websocket: listener unavailable"), health.reasons);
    }

    @Test
    void returnsImmutableDefensiveSnapshots() {
        List<EmulatorStatsService.HealthCheck> checks = new ArrayList<>();
        checks.add(EmulatorStatsService.HealthCheck.healthy("runtime", true, "ready"));

        EmulatorStatsService.HealthSnapshot health = EmulatorStatsService.assessHealth(4_000L, checks);
        checks.clear();

        assertEquals(1, health.checks.size());
        assertThrows(UnsupportedOperationException.class, () -> health.checks.clear());
        assertThrows(UnsupportedOperationException.class, () -> health.reasons.clear());
    }
}
