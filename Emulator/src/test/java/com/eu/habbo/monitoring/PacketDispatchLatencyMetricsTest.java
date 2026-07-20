package com.eu.habbo.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketDispatchLatencyMetricsTest {

    @BeforeEach
    void resetMetrics() {
        PacketDispatchLatencyMetrics.reset();
    }

    @Test
    void snapshotReportsCountAverageP95AndMaximum() {
        PacketDispatchLatencyMetrics.record(TimeUnit.MICROSECONDS.toNanos(100));
        PacketDispatchLatencyMetrics.record(TimeUnit.MILLISECONDS.toNanos(1));
        for (int i = 0; i < 18; i++) {
            PacketDispatchLatencyMetrics.record(TimeUnit.MILLISECONDS.toNanos(12));
        }

        PacketDispatchLatencyMetrics.Snapshot snapshot = PacketDispatchLatencyMetrics.snapshot();

        assertEquals(20, snapshot.samples());
        assertEquals(10.855, snapshot.averageMs(), 0.001);
        assertEquals(25.0, snapshot.p95Ms(), 0.001);
        assertEquals(12.0, snapshot.maxMs(), 0.001);
    }

    @Test
    void snapshotDrainsTheCurrentMeasurementWindow() {
        PacketDispatchLatencyMetrics.record(TimeUnit.MILLISECONDS.toNanos(2));

        assertEquals(1, PacketDispatchLatencyMetrics.snapshot().samples());
        assertEquals(0, PacketDispatchLatencyMetrics.snapshot().samples());
    }
}
