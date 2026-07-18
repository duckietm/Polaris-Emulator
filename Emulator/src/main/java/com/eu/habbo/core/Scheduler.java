package com.eu.habbo.core;

import com.eu.habbo.Emulator;

import java.util.concurrent.atomic.AtomicLong;

public class Scheduler implements Runnable {
    protected boolean disposed;
    protected int interval;
    private volatile long lastStartedEpochMs;
    private volatile long lastCompletedEpochMs;
    private volatile long lastFailedEpochMs;
    private volatile long nextRunEpochMs;
    private final AtomicLong completedRuns = new AtomicLong();
    private final AtomicLong failedRuns = new AtomicLong();
    private volatile String lastError = "";

    public Scheduler(int interval) {
        this.interval = interval;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
        if (disposed) {
            this.nextRunEpochMs = 0L;
        }
    }

    public int getInterval() {
        return this.interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    @Override
    public void run() {
        if (this.disposed) {
            this.nextRunEpochMs = 0L;
            return;
        }

        long delayMs = Math.max(0L, this.interval * 1000L);
        this.markScheduled(System.currentTimeMillis() + delayMs);
        Emulator.getThreading().run(() -> {
            if (this.disposed) {
                this.nextRunEpochMs = 0L;
                return;
            }

            this.markStarted(System.currentTimeMillis());
            try {
                Scheduler.this.run();
                this.markCompleted(System.currentTimeMillis());
            } catch (RuntimeException | Error exception) {
                this.markFailed(System.currentTimeMillis(), exception);
                throw exception;
            }
        }, delayMs);
    }

    void markScheduled(long epochMs) {
        this.nextRunEpochMs = Math.max(0L, epochMs);
    }

    void markStarted(long epochMs) {
        this.lastStartedEpochMs = Math.max(0L, epochMs);
        this.nextRunEpochMs = 0L;
    }

    void markCompleted(long epochMs) {
        this.lastCompletedEpochMs = Math.max(0L, epochMs);
        this.completedRuns.incrementAndGet();
        this.lastError = "";
    }

    void markFailed(long epochMs, Throwable error) {
        this.lastFailedEpochMs = Math.max(0L, epochMs);
        this.failedRuns.incrementAndGet();
        String type = error == null ? "Unknown" : error.getClass().getSimpleName();
        String message = error == null ? "" : error.getMessage();
        String detail = message == null || message.isBlank() ? type : type + ": " + message;
        this.lastError = detail.length() <= 256 ? detail : detail.substring(0, 256);
    }

    public Status snapshot() {
        return new Status(
                this.getClass().getSimpleName(),
                !this.disposed,
                this.interval,
                this.lastStartedEpochMs,
                this.lastCompletedEpochMs,
                this.nextRunEpochMs,
                this.completedRuns.get(),
                this.failedRuns.get(),
                this.lastFailedEpochMs,
                this.lastError
        );
    }

    public static final class Status {
        public final String name;
        public final boolean enabled;
        public final int intervalSeconds;
        public final long lastStartedEpochMs;
        public final long lastCompletedEpochMs;
        public final long nextRunEpochMs;
        public final long completedRuns;
        public final long failedRuns;
        public final long lastFailedEpochMs;
        public final String lastError;

        public Status(String name, boolean enabled, int intervalSeconds, long lastStartedEpochMs, long lastCompletedEpochMs, long nextRunEpochMs, long completedRuns, long failedRuns, long lastFailedEpochMs, String lastError) {
            this.name = name;
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
            this.lastStartedEpochMs = lastStartedEpochMs;
            this.lastCompletedEpochMs = lastCompletedEpochMs;
            this.nextRunEpochMs = nextRunEpochMs;
            this.completedRuns = completedRuns;
            this.failedRuns = failedRuns;
            this.lastFailedEpochMs = lastFailedEpochMs;
            this.lastError = lastError;
        }
    }
}
