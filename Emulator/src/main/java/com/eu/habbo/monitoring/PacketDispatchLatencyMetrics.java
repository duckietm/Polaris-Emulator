package com.eu.habbo.monitoring;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

public final class PacketDispatchLatencyMetrics {

    private static final long[] BUCKET_LIMITS_NANOS = {
        TimeUnit.MICROSECONDS.toNanos(100),
        TimeUnit.MICROSECONDS.toNanos(250),
        TimeUnit.MICROSECONDS.toNanos(500),
        TimeUnit.MILLISECONDS.toNanos(1),
        TimeUnit.MICROSECONDS.toNanos(2_500),
        TimeUnit.MILLISECONDS.toNanos(5),
        TimeUnit.MILLISECONDS.toNanos(10),
        TimeUnit.MILLISECONDS.toNanos(25),
        TimeUnit.MILLISECONDS.toNanos(50),
        TimeUnit.MILLISECONDS.toNanos(100),
        TimeUnit.MILLISECONDS.toNanos(250),
        TimeUnit.MILLISECONDS.toNanos(500),
        TimeUnit.SECONDS.toNanos(1),
        Long.MAX_VALUE
    };

    private static final LongAdder SAMPLES = new LongAdder();
    private static final LongAdder TOTAL_NANOS = new LongAdder();
    private static final AtomicLong MAX_NANOS = new AtomicLong();
    private static final AtomicLongArray BUCKETS = new AtomicLongArray(BUCKET_LIMITS_NANOS.length);

    private PacketDispatchLatencyMetrics() {}

    public static void record(long latencyNanos) {
        long normalized = Math.max(0L, latencyNanos);
        SAMPLES.increment();
        TOTAL_NANOS.add(normalized);
        MAX_NANOS.accumulateAndGet(normalized, Math::max);

        for (int index = 0; index < BUCKET_LIMITS_NANOS.length; index++) {
            if (normalized <= BUCKET_LIMITS_NANOS[index]) {
                BUCKETS.incrementAndGet(index);
                break;
            }
        }
    }

    public static Snapshot snapshot() {
        long samples = SAMPLES.sumThenReset();
        long totalNanos = TOTAL_NANOS.sumThenReset();
        long maxNanos = MAX_NANOS.getAndSet(0L);
        long[] buckets = new long[BUCKET_LIMITS_NANOS.length];
        for (int index = 0; index < buckets.length; index++) {
            buckets[index] = BUCKETS.getAndSet(index, 0L);
        }

        if (samples == 0L) {
            return Snapshot.EMPTY;
        }

        long percentileTarget = Math.max(1L, (long) Math.ceil(samples * 0.95D));
        long cumulative = 0L;
        long p95Nanos = maxNanos;
        for (int index = 0; index < buckets.length; index++) {
            cumulative += buckets[index];
            if (cumulative >= percentileTarget) {
                p95Nanos = BUCKET_LIMITS_NANOS[index] == Long.MAX_VALUE ? maxNanos : BUCKET_LIMITS_NANOS[index];
                break;
            }
        }

        return new Snapshot(
                samples,
                nanosToMillis((double) totalNanos / samples),
                nanosToMillis(p95Nanos),
                nanosToMillis(maxNanos));
    }

    static void reset() {
        snapshot();
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000D;
    }

    public record Snapshot(long samples, double averageMs, double p95Ms, double maxMs) {

        private static final Snapshot EMPTY = new Snapshot(0L, 0D, 0D, 0D);
    }
}
