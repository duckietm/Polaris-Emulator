package com.eu.habbo.database;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded executor for blocking database writes that must not compete with
 * room and wired scheduling.
 */
public final class PersistenceExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceExecutor.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 2_048;
    private static final int RECENT_FAILURE_CAPACITY = 64;

    private final PersistenceOperationMonitor operationMonitor =
            new PersistenceOperationMonitor(RECENT_FAILURE_CAPACITY);
    private final int queueCapacity;
    private final AtomicInteger highWaterMark = new AtomicInteger();
    private final AtomicLong saturationCount = new AtomicLong();
    private final AtomicLong totalSubmissionWaitNanos = new AtomicLong();
    private final ThreadLocal<Boolean> persistenceWorker = ThreadLocal.withInitial(() -> false);
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PersistenceExecutor(int threads, int queueCapacity) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }

        this.queueCapacity = queueCapacity;
        ThreadFactory platformThreadFactory =
                Thread.ofPlatform().name("Polaris-JDBC-", 0).factory();
        ThreadFactory threadFactory = task -> platformThreadFactory.newThread(() -> {
            this.persistenceWorker.set(true);
            try {
                task.run();
            } finally {
                this.persistenceWorker.remove();
            }
        });
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                this::enqueueOnSaturation);
        this.executor.prestartAllCoreThreads();
    }

    public static PersistenceExecutor forRuntimeThreads(int runtimeThreads) {
        return forRuntimeThreads(runtimeThreads, DEFAULT_QUEUE_CAPACITY);
    }

    public static PersistenceExecutor forRuntimeThreads(int runtimeThreads, int queueCapacity) {
        int threads = Math.max(2, Math.min(8, runtimeThreads));
        return new PersistenceExecutor(threads, queueCapacity);
    }

    public void execute(Runnable task) {
        Runnable requiredTask = Objects.requireNonNull(task, "task");
        this.execute(operationType(requiredTask), requiredTask);
    }

    public void execute(String operationType, Runnable task) {
        Runnable requiredTask = Objects.requireNonNull(task, "task");
        PersistenceOperationMonitor.Operation operation = this.operationMonitor.started(operationType);
        Runnable guarded = this.guard(operation, requiredTask);
        try {
            if (!this.accepting.get()) {
                throw new RejectedExecutionException("Persistence executor is not accepting work");
            }

            this.executor.execute(guarded);
            this.recordQueueDepth();
        } catch (RejectedExecutionException exception) {
            this.operationMonitor.rejected(operation, exception);
            throw exception;
        }
    }

    public int getQueueDepth() {
        return this.executor.getQueue().size();
    }

    public int getActiveCount() {
        return this.executor.getActiveCount();
    }

    public PersistenceOperationMonitor.Snapshot operationSnapshot() {
        return this.operationMonitor.snapshot();
    }

    public Metrics metrics() {
        return new Metrics(
                this.getActiveCount(),
                this.getQueueDepth(),
                this.queueCapacity,
                this.highWaterMark.get(),
                this.saturationCount.get(),
                this.totalSubmissionWaitNanos.get(),
                this.accepting.get());
    }

    public void shutDown() {
        this.shutDown(35, TimeUnit.SECONDS);
    }

    void shutDown(long timeout, TimeUnit unit) {
        this.accepting.set(false);
        this.executor.shutdown();

        boolean interrupted = false;
        boolean terminated = false;
        try {
            terminated = this.executor.awaitTermination(Math.max(0L, timeout), unit);
        } catch (InterruptedException exception) {
            interrupted = true;
        }

        if (!terminated) {
            List<Runnable> queued = this.executor.shutdownNow();
            for (Runnable task : queued) {
                task.run();
            }
            if (!interrupted) {
                try {
                    terminated = this.executor.awaitTermination(Math.max(0L, timeout), unit);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (terminated) {
            LOGGER.info("Persistence executor stopped after draining accepted work");
        } else {
            LOGGER.error("Persistence executor workers remained active during shutdown");
        }
    }

    private void enqueueOnSaturation(Runnable task, ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("Persistence executor is shutting down");
        }

        this.saturationCount.incrementAndGet();
        this.highWaterMark.accumulateAndGet(this.queueCapacity, Math::max);
        if (this.persistenceWorker.get()) {
            task.run();
            return;
        }

        long startedAt = System.nanoTime();
        try {
            executor.getQueue().put(task);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for persistence capacity", exception);
        } finally {
            this.totalSubmissionWaitNanos.addAndGet(Math.max(0L, System.nanoTime() - startedAt));
        }

        if (executor.isShutdown() && executor.remove(task)) {
            throw new RejectedExecutionException("Persistence executor shut down while accepting work");
        }

        this.recordQueueDepth();
    }

    private void recordQueueDepth() {
        this.highWaterMark.accumulateAndGet(this.executor.getQueue().size(), Math::max);
    }

    private Runnable guard(PersistenceOperationMonitor.Operation operation, Runnable task) {
        return () -> {
            try {
                task.run();
                this.operationMonitor.succeeded(operation);
            } catch (Exception exception) {
                this.operationMonitor.failed(operation, exception);
                LOGGER.error(
                        "Persistence task failed: operationId={}, operationType={}",
                        operation.operationId(),
                        operation.operationType(),
                        exception);
            } catch (Error error) {
                this.operationMonitor.failed(operation, error);
                LOGGER.error(
                        "Persistence task failed: operationId={}, operationType={}",
                        operation.operationId(),
                        operation.operationType(),
                        error);
                throw error;
            }
        };
    }

    private static String operationType(Runnable task) {
        String simpleName = task.getClass().getSimpleName();
        return simpleName.isBlank() || simpleName.contains("$$Lambda") ? "anonymous" : simpleName;
    }

    public record Metrics(
            int activeCount,
            int queueDepth,
            int queueCapacity,
            int highWaterMark,
            long saturationCount,
            long totalSubmissionWaitNanos,
            boolean accepting) {}
}
