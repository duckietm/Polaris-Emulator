package com.eu.habbo.monitoring;

import java.util.concurrent.atomic.LongAdder;

public final class ExecutionBackpressureMetrics {
    private static final LongAdder PACKET_REJECTED_TASKS = new LongAdder();
    private static final LongAdder BLOCKING_HTTP_REJECTED_TASKS = new LongAdder();

    private ExecutionBackpressureMetrics() {}

    public static void recordRejection(Lane lane) {
        switch (lane) {
            case GAME_PACKET -> PACKET_REJECTED_TASKS.increment();
            case BLOCKING_HTTP -> BLOCKING_HTTP_REJECTED_TASKS.increment();
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(PACKET_REJECTED_TASKS.sum(), BLOCKING_HTTP_REJECTED_TASKS.sum());
    }

    public enum Lane {
        GAME_PACKET,
        BLOCKING_HTTP
    }

    public record Snapshot(long packetRejectedTasks, long blockingHttpRejectedTasks) {}
}
