package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.networking.gameserver.ExecutionBackpressureStatus;
import org.junit.jupiter.api.Test;

class NetworkDispatchMetricsContractTest {

    @Test
    void networkSnapshotExposesDispatchLatencyWindow() {
        ExecutionBackpressureStatus.Snapshot packet = new ExecutionBackpressureStatus.Snapshot(
                "enforce", 100, 75, 20, 80, 3, 2, 1, 4, 1, 4, 3, 1, 0, 0, 0, 25, 10);
        ExecutionBackpressureStatus.Snapshot http = new ExecutionBackpressureStatus.Snapshot(
                "observe", 50, 37, 2, 7, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0);
        EmulatorStatsService.NetworkMetrics metrics =
                new EmulatorStatsService.NetworkMetrics(1D, 2D, 3D, 4D, 5L, 6L, 7L, 8D, 9D, 10D, packet, http);

        assertEquals(7L, metrics.dispatchSamples);
        assertEquals(8D, metrics.dispatchAverageMs);
        assertEquals(9D, metrics.dispatchP95Ms);
        assertEquals(10D, metrics.dispatchMaxMs);
        assertEquals(80, metrics.packetBackpressure.highWatermark());
        assertEquals(3, metrics.packetBackpressure.wouldThrottle());
        assertEquals(2, metrics.httpBackpressure.httpRejections());
    }
}
