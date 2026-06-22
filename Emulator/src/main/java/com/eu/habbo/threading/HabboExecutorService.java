package com.eu.habbo.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

public class HabboExecutorService extends ScheduledThreadPoolExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabboExecutorService.class);

    public HabboExecutorService(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        // Tasks submitted via submit()/schedule() capture their failure inside the
        // Future instead of surfacing it as afterExecute's `t`, so without this a
        // failing background/scheduled task (e.g. a direct getService().submit())
        // dies silently. A still-pending periodic task isn't done yet, so get()
        // won't block; a normally-cancelled task throws CancellationException,
        // which is not an error.
        if (t == null && r instanceof Future<?> future && future.isDone()) {
            try {
                future.get();
            } catch (CancellationException ignored) {
                // normal cancellation (shutdownNow / cancelled periodic task)
            } catch (ExecutionException e) {
                t = e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (t != null && !(t instanceof IOException)) {
            LOGGER.error("Error in HabboExecutorService", t);
        }
    }
}
