package com.eu.habbo.database;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded executor for blocking database writes that must not compete with
 * room and wired scheduling.
 */
public final class PersistenceExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceExecutor.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 2_048;

    private final ThreadPoolExecutor executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PersistenceExecutor(int threads, int queueCapacity) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }

        ThreadFactory threadFactory =
                Thread.ofPlatform().name("Polaris-JDBC-", 0).factory();
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                (task, ignored) -> task.run());
        this.executor.prestartAllCoreThreads();
    }

    public static PersistenceExecutor forRuntimeThreads(int runtimeThreads) {
        int threads = Math.max(2, Math.min(8, runtimeThreads));
        return new PersistenceExecutor(threads, DEFAULT_QUEUE_CAPACITY);
    }

    public void execute(Runnable task) {
        Runnable guarded = guard(Objects.requireNonNull(task, "task"));
        if (!this.accepting.get()) {
            guarded.run();
            return;
        }

        this.executor.execute(guarded);
    }

    public int getQueueDepth() {
        return this.executor.getQueue().size();
    }

    public int getActiveCount() {
        return this.executor.getActiveCount();
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

    private static Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception exception) {
                LOGGER.error("Persistence task failed", exception);
            }
        };
    }
}
