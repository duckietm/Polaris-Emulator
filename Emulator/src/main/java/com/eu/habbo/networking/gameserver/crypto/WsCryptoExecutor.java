package com.eu.habbo.networking.gameserver.crypto;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WsCryptoExecutor {

    private static final int QUEUE_CAPACITY = 256;
    private static final ThreadPoolExecutor EXECUTOR = createExecutor(defaultWidth());

    private WsCryptoExecutor() {}

    static Executor executor() {
        return EXECUTOR;
    }

    static ThreadPoolExecutor createExecutor(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("WebSocket crypto executor width " + "must be positive");
        }

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                width,
                width,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new java.util.concurrent.ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "ws-crypto-worker-" + this.counter.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static int defaultWidth() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(8, processors / 2));
    }
}
