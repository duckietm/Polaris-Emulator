package com.eu.habbo.database;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class PersistenceOperationMonitor {
    private final int failureCapacity;
    private final LongSupplier nanoTime;
    private final LongSupplier epochMillis;
    private final AtomicLong nextOperationId = new AtomicLong();
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong succeededCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong activeCount = new AtomicLong();
    private final AtomicLong totalDurationNanos = new AtomicLong();
    private final AtomicLong maxDurationNanos = new AtomicLong();
    private final ArrayDeque<RecentFailure> recentFailures = new ArrayDeque<>();

    public PersistenceOperationMonitor(int failureCapacity) {
        this(failureCapacity, System::nanoTime, System::currentTimeMillis);
    }

    PersistenceOperationMonitor(int failureCapacity, LongSupplier nanoTime, LongSupplier epochMillis) {
        if (failureCapacity < 1) {
            throw new IllegalArgumentException("failureCapacity must be positive");
        }
        this.failureCapacity = failureCapacity;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.epochMillis = Objects.requireNonNull(epochMillis, "epochMillis");
    }

    Operation started(String operationType) {
        long operationId = this.nextOperationId.incrementAndGet();
        this.submittedCount.incrementAndGet();
        this.activeCount.incrementAndGet();
        return new Operation(
                operationId,
                normalizeOperationType(operationType),
                this.epochMillis.getAsLong(),
                this.nanoTime.getAsLong());
    }

    void succeeded(Operation operation) {
        this.succeededCount.incrementAndGet();
        this.complete(operation);
    }

    void failed(Operation operation, Throwable failure) {
        this.failedCount.incrementAndGet();
        this.recordFailure(operation, "FAILED", failure, this.complete(operation));
    }

    void rejected(Operation operation, Throwable failure) {
        this.rejectedCount.incrementAndGet();
        this.recordFailure(operation, "REJECTED", failure, this.complete(operation));
    }

    public Snapshot snapshot() {
        List<RecentFailure> failures;
        synchronized (this.recentFailures) {
            failures = List.copyOf(this.recentFailures);
        }
        return new Snapshot(
                this.submittedCount.get(),
                this.succeededCount.get(),
                this.failedCount.get(),
                this.rejectedCount.get(),
                this.activeCount.get(),
                this.totalDurationNanos.get(),
                this.maxDurationNanos.get(),
                failures);
    }

    private long complete(Operation operation) {
        long durationNanos = Math.max(0L, this.nanoTime.getAsLong() - operation.startedAtNanos());
        this.activeCount.decrementAndGet();
        this.totalDurationNanos.addAndGet(durationNanos);
        this.maxDurationNanos.accumulateAndGet(durationNanos, Math::max);
        return durationNanos;
    }

    private void recordFailure(Operation operation, String outcome, Throwable failure, long durationNanos) {
        RecentFailure recentFailure = new RecentFailure(
                operation.operationId(),
                operation.operationType(),
                outcome,
                operation.startedAtEpochMs(),
                durationNanos,
                errorType(failure));
        synchronized (this.recentFailures) {
            this.recentFailures.addFirst(recentFailure);
            while (this.recentFailures.size() > this.failureCapacity) {
                this.recentFailures.removeLast();
            }
        }
    }

    private static String normalizeOperationType(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return "unknown";
        }
        return operationType.strip();
    }

    private static String errorType(Throwable failure) {
        if (failure == null) {
            return "UnknownFailure";
        }
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }

    record Operation(long operationId, String operationType, long startedAtEpochMs, long startedAtNanos) {}

    public record RecentFailure(
            long operationId,
            String operationType,
            String outcome,
            long startedAtEpochMs,
            long durationNanos,
            String errorType) {}

    public record Snapshot(
            long submittedCount,
            long succeededCount,
            long failedCount,
            long rejectedCount,
            long activeCount,
            long totalDurationNanos,
            long maxDurationNanos,
            List<RecentFailure> recentFailures) {}
}
