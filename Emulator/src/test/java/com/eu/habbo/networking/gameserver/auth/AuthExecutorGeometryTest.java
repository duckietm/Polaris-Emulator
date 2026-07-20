package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AuthExecutorGeometryTest {

    @Test
    void configuredWidthIsUsedBeforeRequestsEnterTheQueue() {
        ThreadPoolExecutor executor = AuthHttpHandler.createAuthExecutor(6);
        try {
            assertEquals(6, executor.getCorePoolSize());
            assertEquals(6, executor.getMaximumPoolSize());
            assertEquals(512, executor.getQueue().remainingCapacity());
            assertTrue(executor.allowsCoreThreadTimeOut());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void fillsConfiguredWorkersBeforeQueueingTheNextRequest() throws Exception {
        ThreadPoolExecutor executor = AuthHttpHandler.createAuthExecutor(3);
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocking = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            executor.execute(blocking);
            executor.execute(blocking);
            executor.execute(blocking);

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(0, executor.getQueue().size());

            executor.execute(() -> {});

            assertEquals(1, executor.getQueue().size());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
