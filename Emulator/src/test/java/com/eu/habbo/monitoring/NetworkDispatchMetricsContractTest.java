package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NetworkDispatchMetricsContractTest {

    @Test
    void networkSnapshotExposesDispatchLatencyWindow() {
        EmulatorStatsService.NetworkMetrics metrics =
                new EmulatorStatsService.NetworkMetrics(1D, 2D, 3D, 4D, 5L, 6L, 7L, 8D, 9D, 10D);

        assertEquals(7L, metrics.dispatchSamples);
        assertEquals(8D, metrics.dispatchAverageMs);
        assertEquals(9D, metrics.dispatchP95Ms);
        assertEquals(10D, metrics.dispatchMaxMs);
    }
}
