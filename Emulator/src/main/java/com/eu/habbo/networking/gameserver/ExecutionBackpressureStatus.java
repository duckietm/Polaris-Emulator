package com.eu.habbo.networking.gameserver;

import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;

public final class ExecutionBackpressureStatus {
    public record Snapshot(
            String mode,
            int capacity,
            int lowWatermark,
            int inFlight,
            int highWatermark,
            long wouldThrottle,
            long rejections,
            int waitingConnections,
            long affectedConnections,
            int activePauses,
            long pauseEvents,
            long resumes,
            long timeoutDisconnects,
            long overflowDisconnects,
            long httpRejections,
            long dispatchFailures,
            long totalPauseMillis,
            long longestPauseMillis) {
        public static Snapshot inactive() {
            return new Snapshot("inactive", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private ExecutionBackpressureStatus() {}

    public static Snapshot packet() {
        return GamePacketExecutionGroup.statusSnapshot();
    }

    public static Snapshot http() {
        return BlockingHttpExecutionGroup.statusSnapshot();
    }

    static Snapshot combine(
            ExecutionCapacityController.Snapshot capacity, ExecutionBackpressureMetrics.Snapshot metrics) {
        if (capacity == null || metrics == null) {
            return Snapshot.inactive();
        }
        return new Snapshot(
                capacity.mode().name().toLowerCase(java.util.Locale.ROOT),
                capacity.capacity(),
                capacity.lowWatermark(),
                capacity.inFlight(),
                capacity.highWatermark(),
                capacity.wouldThrottle(),
                capacity.rejections(),
                capacity.waitingConnections(),
                metrics.affectedConnections(),
                metrics.activePauses(),
                metrics.pauseEvents(),
                metrics.resumes(),
                metrics.timeoutDisconnects(),
                metrics.overflowDisconnects(),
                metrics.httpRejections(),
                metrics.dispatchFailures(),
                metrics.totalPauseMillis(),
                metrics.longestPauseMillis());
    }
}
