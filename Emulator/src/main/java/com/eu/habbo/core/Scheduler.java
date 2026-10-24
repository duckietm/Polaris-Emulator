package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import java.util.concurrent.atomic.AtomicLong;

public class Scheduler implements Runnable {
    protected volatile boolean disposed;
    protected int interval;

    /**
     * Which chain of ticks is the live one.
     *
     * <p>Each tick schedules the next, and stopped only by reading {@code disposed}.
     * Turning a scheduler off and on again inside one interval therefore left the
     * queued tick of the old chain to wake up to {@code disposed == false} and carry on
     * beside the chain the restart had begun: two chains paying every online user, every
     * interval, until the next restart of the hotel.
     *
     * <p>A restart bumps this, and a tick from a chain that has been replaced exits.
     */
    private final java.util.concurrent.atomic.AtomicInteger generation =
            new java.util.concurrent.atomic.AtomicInteger();

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

    /**
     * Enables this scheduler and starts its chain of ticks, replacing any chain already
     * running. Safe to call repeatedly: there is never more than one live chain.
     */
    public void restart() {
        this.disposed = false;
        this.run();
    }

    /** Starts the chain. The work of one tick belongs in {@link #tick()}. */
    @Override
    public void run() {
        if (this.disposed) {
            this.nextRunEpochMs = 0L;
            return;
        }

        this.scheduleNext(this.generation.incrementAndGet());
    }

    private void scheduleNext(int chain) {
        long delayMs = Math.max(0L, this.interval * 1000L);
        this.markScheduled(System.currentTimeMillis() + delayMs);
        Emulator.getThreading().run(() -> this.runChain(chain), delayMs);
    }

    private void runChain(int chain) {
        if (this.disposed || this.generation.get() != chain) {
            this.nextRunEpochMs = 0L;
            return;
        }

        this.markStarted(System.currentTimeMillis());
        try {
            this.tick();
            this.markCompleted(System.currentTimeMillis());
        } catch (RuntimeException | Error exception) {
            this.markFailed(System.currentTimeMillis(), exception);
            throw exception;
        } finally {
            // Keep the chain alive even when a tick threw, as scheduling the next one up
            // front used to do - but only while this chain is still the live one.
            if (!this.disposed && this.generation.get() == chain) {
                this.scheduleNext(chain);
            }
        }
    }

    /**
     * The work of one tick, called by the base only while the scheduler is enabled and
     * only from the live chain. A subclass puts its payload here instead of overriding
     * {@link #run()}: a payout written in an override ran even when the base had given
     * up on the tick.
     */
    protected void tick() {}

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
                this.lastError);
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

        public Status(
                String name,
                boolean enabled,
                int intervalSeconds,
                long lastStartedEpochMs,
                long lastCompletedEpochMs,
                long nextRunEpochMs,
                long completedRuns,
                long failedRuns,
                long lastFailedEpochMs,
                String lastError) {
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
