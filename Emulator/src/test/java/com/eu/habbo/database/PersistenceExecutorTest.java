package com.eu.habbo.database;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceExecutorTest {

    @Test
    void databaseWorkRunsOnDedicatedNamedWorkers()
            throws Exception {
        PersistenceExecutor executor =
                new PersistenceExecutor(2, 8);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> threadName =
                new AtomicReference<>();
        try {
            executor.execute(() -> {
                threadName.set(
                        Thread.currentThread().getName());
                completed.countDown();
            });

            assertTrue(completed.await(
                    2,
                    TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith(
                    "Polaris-JDBC-"));
        } finally {
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void saturationAppliesCallerBackpressureWithoutDroppingWork()
            throws Exception {
        PersistenceExecutor executor =
                new PersistenceExecutor(1, 1);
        CountDownLatch workerStarted =
                new CountDownLatch(1);
        CountDownLatch releaseWorker =
                new CountDownLatch(1);
        AtomicReference<Thread> saturatedTaskThread =
                new AtomicReference<>();
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(
                    2,
                    TimeUnit.SECONDS));
            executor.execute(() -> {
            });

            Thread caller = Thread.currentThread();
            executor.execute(() ->
                    saturatedTaskThread.set(
                            Thread.currentThread()));

            assertEquals(
                    caller,
                    saturatedTaskThread.get());
        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdownDrainsAcceptedTasks() {
        PersistenceExecutor executor =
                new PersistenceExecutor(1, 8);
        CountDownLatch completed = new CountDownLatch(2);
        executor.execute(completed::countDown);
        executor.execute(completed::countDown);

        executor.shutDown(2, TimeUnit.SECONDS);

        assertEquals(0L, completed.getCount());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
