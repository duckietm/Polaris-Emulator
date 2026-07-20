package com.eu.habbo.threading;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.PersistenceExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPooling {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPooling.class);

    public final int threads;
    private final ScheduledExecutorService scheduledPool;
    private final PersistenceExecutor persistenceExecutor;
    private volatile boolean canAdd;

    public ThreadPooling(Integer threads) {
        this(threads, null);
    }

    public ThreadPooling(Integer threads, PersistenceExecutor persistenceExecutor) {
        this.threads = threads;
        this.scheduledPool = new HabboExecutorService(this.threads, new DefaultThreadFactory("HabExec"));
        this.persistenceExecutor = persistenceExecutor;
        this.canAdd = true;
        LOGGER.info("Thread Pool -> Loaded!");
    }

    public ScheduledFuture<?> run(Runnable run) {
        try {
            if (this.canAdd) {
                return this.run(run, 0);
            } else {
                if (Emulator.isShuttingDown) {
                    run.run();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public ScheduledFuture<?> run(Runnable run, long delay) {
        try {
            if (this.canAdd) {
                return this.scheduledPool.schedule(
                        () -> {
                            try {
                                run.run();
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        },
                        delay,
                        TimeUnit.MILLISECONDS);
            } else {
                LOGGER.warn("Rejected delayed task during shutdown (delay: {} ms)", delay);
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    public void runPersistence(Runnable task) {
        if (this.persistenceExecutor == null) {
            this.run(task);
            return;
        }

        this.persistenceExecutor.execute(task);
    }

    public void shutDown() {
        this.shutDown(5, TimeUnit.SECONDS);
    }

    ShutdownResult shutDown(long timeout, TimeUnit unit) {
        this.canAdd = false;
        this.scheduledPool.shutdown();

        boolean terminatedGracefully = false;
        boolean terminated = false;
        boolean interrupted = false;
        int cancelledTasks = 0;

        try {
            terminatedGracefully = this.scheduledPool.awaitTermination(Math.max(0L, timeout), unit);
        } catch (InterruptedException exception) {
            interrupted = true;
        }

        if (!terminatedGracefully) {
            cancelledTasks = this.scheduledPool.shutdownNow().size();

            if (!interrupted) {
                try {
                    terminated = this.scheduledPool.awaitTermination(Math.max(0L, timeout), unit);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } else {
            terminated = true;
        }

        long completedTasks = this.scheduledPool instanceof ScheduledThreadPoolExecutor executor
                ? executor.getCompletedTaskCount()
                : 0L;

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        ShutdownResult result =
                new ShutdownResult(terminatedGracefully, terminated, cancelledTasks, completedTasks, interrupted);

        if (terminatedGracefully) {
            LOGGER.info("Threading -> Disposed gracefully (completed tasks: {})", completedTasks);
        } else if (terminated) {
            LOGGER.warn(
                    "Threading -> Disposed after forced shutdown (completed tasks: {}, cancelled queued tasks: {})",
                    completedTasks,
                    cancelledTasks);
        } else {
            LOGGER.error(
                    "Threading -> Workers still active after forced shutdown (completed tasks: {}, cancelled queued tasks: {}, interrupted: {})",
                    completedTasks,
                    cancelledTasks,
                    interrupted);
        }

        return result;
    }

    record ShutdownResult(
            boolean terminatedGracefully,
            boolean terminated,
            int cancelledTasks,
            long completedTasks,
            boolean interrupted) {}

    public void setCanAdd(boolean canAdd) {
        this.canAdd = canAdd;
    }

    public ScheduledExecutorService getService() {
        return this.scheduledPool;
    }
}
