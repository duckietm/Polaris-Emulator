package com.srth.plugin.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoleplayMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleplayMetrics.class);
    private static final long[] LATENCY_BOUNDS_NANOS = {
            1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L, 500_000_000L
    };
    private static final ConcurrentMap<String, OperationMetric> OPERATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong ACTIVE_WEBSOCKET_CONNECTIONS = new AtomicLong();

    private static ScheduledExecutorService reporter;

    private RoleplayMetrics() {
    }

    public static synchronized void start() {
        if (reporter != null) {
            return;
        }

        reporter = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "roleplay-metrics-reporter");
            thread.setDaemon(true);
            return thread;
        });
        reporter.scheduleAtFixedRate(RoleplayMetrics::report, 1, 1, TimeUnit.MINUTES);
    }

    public static synchronized void stop() {
        if (reporter != null) {
            reporter.shutdownNow();
            reporter = null;
        }
        report();
    }

    public static void record(String operation, long startedAtNanos, boolean failed) {
        long durationNanos = Math.max(0, System.nanoTime() - startedAtNanos);
        OPERATIONS.computeIfAbsent(operation, ignored -> new OperationMetric())
                .record(durationNanos, failed);
    }

    public static void measureChecked(String operation, CheckedRunnable action) throws Exception {
        long startedAtNanos = System.nanoTime();
        boolean failed = true;
        try {
            action.run();
            failed = false;
        } finally {
            record(operation, startedAtNanos, failed);
        }
    }

    public static void measureUnchecked(String operation, Runnable action) {
        long startedAtNanos = System.nanoTime();
        boolean failed = true;
        try {
            action.run();
            failed = false;
        } finally {
            record(operation, startedAtNanos, failed);
        }
    }

    public static void websocketConnected() {
        ACTIVE_WEBSOCKET_CONNECTIONS.incrementAndGet();
    }

    public static void websocketDisconnected() {
        ACTIVE_WEBSOCKET_CONNECTIONS.updateAndGet(count -> Math.max(0, count - 1));
    }

    private static void report() {
        OPERATIONS.forEach((name, metric) -> metric.report(name));

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        LOGGER.info("[RP Metrics] websocket.activeConnections={} jvm.heapUsedMb={} jvm.heapMaxMb={}",
                ACTIVE_WEBSOCKET_CONNECTIONS.get(),
                toMegabytes(heap.getUsed()),
                toMegabytes(heap.getMax()));
    }

    private static long toMegabytes(long bytes) {
        return bytes < 0 ? bytes : bytes / (1024 * 1024);
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class OperationMetric {
        private final LongAdder calls = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private final LongAdder[] latencyBuckets = new LongAdder[LATENCY_BOUNDS_NANOS.length + 1];

        private OperationMetric() {
            for (int i = 0; i < latencyBuckets.length; i++) {
                latencyBuckets[i] = new LongAdder();
            }
        }

        private void record(long durationNanos, boolean failed) {
            calls.increment();
            if (failed) {
                failures.increment();
            }
            totalNanos.add(durationNanos);
            maxNanos.accumulateAndGet(durationNanos, Math::max);

            int bucket = 0;
            while (bucket < LATENCY_BOUNDS_NANOS.length
                    && durationNanos > LATENCY_BOUNDS_NANOS[bucket]) {
                bucket++;
            }
            latencyBuckets[bucket].increment();
        }

        private void report(String name) {
            long callCount = calls.sumThenReset();
            long failureCount = failures.sumThenReset();
            long elapsedNanos = totalNanos.sumThenReset();
            long maxDurationNanos = maxNanos.getAndSet(0);
            long[] bucketCounts = new long[latencyBuckets.length];
            for (int i = 0; i < latencyBuckets.length; i++) {
                bucketCounts[i] = latencyBuckets[i].sumThenReset();
            }

            if (callCount == 0) {
                return;
            }

            double averageMillis = elapsedNanos / (double) callCount / 1_000_000.0;
            long percentileTarget = Math.max(1, (callCount * 95 + 99) / 100);
            String percentileBucket = ">500ms";
            long cumulativeCount = 0;
            for (int i = 0; i < bucketCounts.length; i++) {
                cumulativeCount += bucketCounts[i];
                if (cumulativeCount >= percentileTarget) {
                    percentileBucket = i < LATENCY_BOUNDS_NANOS.length
                            ? "<=" + (LATENCY_BOUNDS_NANOS[i] / 1_000_000) + "ms"
                            : ">500ms";
                    break;
                }
            }

            LOGGER.info("[RP Metrics] operation={} calls={} failures={} avgMs={} p95Bucket={} maxMs={}",
                    name, callCount, failureCount, Math.round(averageMillis * 100.0) / 100.0,
                    percentileBucket, maxDurationNanos / 1_000_000.0);
        }
    }
}
