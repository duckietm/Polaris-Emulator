package com.eu.habbo.monitoring;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class ExecutionBackpressureMetrics {
    public record Snapshot(
            String lane,
            long affectedConnections,
            int activePauses,
            long pauseEvents,
            long resumes,
            long timeoutDisconnects,
            long overflowDisconnects,
            long httpRejections,
            long dispatchFailures,
            long totalPauseMillis,
            long longestPauseMillis) {}

    private final String lane;
    private final LongAdder affectedConnections = new LongAdder();
    private final AtomicInteger activePauses = new AtomicInteger();
    private final LongAdder pauseEvents = new LongAdder();
    private final LongAdder resumes = new LongAdder();
    private final LongAdder timeoutDisconnects = new LongAdder();
    private final LongAdder overflowDisconnects = new LongAdder();
    private final LongAdder httpRejections = new LongAdder();
    private final LongAdder dispatchFailures = new LongAdder();
    private final LongAdder totalPauseNanos = new LongAdder();
    private final AtomicLong longestPauseNanos = new AtomicLong();

    public ExecutionBackpressureMetrics(String lane) {
        this.lane = Objects.requireNonNull(lane, "lane");
    }

    public void recordAffectedConnection() {
        this.affectedConnections.increment();
    }

    public void recordPauseStarted() {
        this.pauseEvents.increment();
        this.activePauses.incrementAndGet();
    }

    public void recordPauseEnded(long durationNanos, boolean resumed) {
        long boundedDuration = Math.max(0L, durationNanos);
        this.activePauses.updateAndGet(current -> Math.max(0, current - 1));
        this.totalPauseNanos.add(boundedDuration);
        this.longestPauseNanos.accumulateAndGet(boundedDuration, Math::max);
        if (resumed) {
            this.resumes.increment();
        }
    }

    public void recordTimeoutDisconnect() {
        this.timeoutDisconnects.increment();
    }

    public void recordOverflowDisconnect() {
        this.overflowDisconnects.increment();
    }

    public void recordHttpRejection() {
        this.httpRejections.increment();
    }

    public void recordDispatchFailure() {
        this.dispatchFailures.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                this.lane,
                this.affectedConnections.sum(),
                this.activePauses.get(),
                this.pauseEvents.sum(),
                this.resumes.sum(),
                this.timeoutDisconnects.sum(),
                this.overflowDisconnects.sum(),
                this.httpRejections.sum(),
                this.dispatchFailures.sum(),
                this.totalPauseNanos.sum() / 1_000_000L,
                this.longestPauseNanos.get() / 1_000_000L);
    }
}
