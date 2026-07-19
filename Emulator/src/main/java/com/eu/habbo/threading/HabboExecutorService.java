package com.eu.habbo.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class HabboExecutorService extends ScheduledThreadPoolExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabboExecutorService.class);

    public HabboExecutorService(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory, RejectedExecutionHandlerImpl.aborting());
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return super.scheduleAtFixedRate(guardPeriodicTask(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return super.scheduleWithFixedDelay(guardPeriodicTask(command), initialDelay, delay, unit);
    }

    private Runnable guardPeriodicTask(Runnable command) {
        Objects.requireNonNull(command, "command");
        return () -> {
            try {
                command.run();
            } catch (Exception exception) {
                logFailure(exception);
            }
        };
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        Throwable failure = t;
        if (failure == null && r instanceof Future<?> future && future.isDone()) {
            try {
                future.get();
            } catch (CancellationException ignored) {
                return;
            } catch (ExecutionException exception) {
                failure = exception.getCause();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (failure != null) {
            logFailure(failure);
        }
    }

    private void logFailure(Throwable failure) {
        LOGGER.error("Error in HabboExecutorService", failure);
    }
}
