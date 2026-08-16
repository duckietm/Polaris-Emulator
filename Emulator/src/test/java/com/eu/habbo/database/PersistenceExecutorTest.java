package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PersistenceExecutorTest {

    @Test
    void databaseWorkRunsOnDedicatedNamedWorkers() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(2, 8);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        try {
            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                completed.countDown();
            });

            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith("Polaris-JDBC-"));
        } finally {
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void saturationAppliesCallerBackpressureWithoutDroppingWork() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<Thread> saturatedTaskThread = new AtomicReference<>();
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {});

            Thread caller = Thread.currentThread();
            executor.execute(() -> saturatedTaskThread.set(Thread.currentThread()));

            assertEquals(caller, saturatedTaskThread.get());
        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void shutdownDrainsAcceptedTasks() {
        PersistenceExecutor executor = new PersistenceExecutor(1, 8);
        CountDownLatch completed = new CountDownLatch(2);
        executor.execute(completed::countDown);
        executor.execute(completed::countDown);

        executor.shutDown(2, TimeUnit.SECONDS);

        assertEquals(0L, completed.getCount());
    }

    @Test
    void operationSnapshotIdentifiesFailuresAndTracksSuccessfulWork() {
        PersistenceExecutor executor = new PersistenceExecutor(1, 8);
        try {
            executor.execute(new FailingPersistenceTask());
            executor.execute("test.barrier", () -> {});
            executor.shutDown(2, TimeUnit.SECONDS);

            PersistenceOperationMonitor.Snapshot snapshot = executor.operationSnapshot();
            assertEquals(2L, snapshot.submittedCount());
            assertEquals(1L, snapshot.succeededCount());
            assertEquals(1L, snapshot.failedCount());
            assertEquals(0L, snapshot.activeCount());
            assertEquals(1, snapshot.recentFailures().size());
            assertEquals(
                    "FailingPersistenceTask", snapshot.recentFailures().get(0).operationType());
            assertEquals(
                    "IllegalStateException", snapshot.recentFailures().get(0).errorType());
            assertTrue(snapshot.recentFailures().get(0).operationId() > 0L);
        } finally {
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectedNullTaskDoesNotCreatePhantomActiveOperation() {
        PersistenceExecutor executor = new PersistenceExecutor(1, 8);
        try {
            assertThrows(NullPointerException.class, () -> executor.execute("invalid", null));

            PersistenceOperationMonitor.Snapshot snapshot = executor.operationSnapshot();
            assertEquals(0L, snapshot.submittedCount());
            assertEquals(0L, snapshot.activeCount());
        } finally {
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void capacityMetricsRemainAvailableAlongsideOperationTelemetry() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute("test.blocking", () -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute("test.queued", () -> {});

            PersistenceExecutor.Metrics metrics = executor.metrics();
            PersistenceOperationMonitor.Snapshot operations = executor.operationSnapshot();

            assertEquals(1, metrics.activeCount());
            assertEquals(1, metrics.queueDepth());
            assertEquals(1, metrics.queueCapacity());
            assertEquals(2L, operations.submittedCount());
        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FailingPersistenceTask implements Runnable {
        @Override
        public void run() {
            throw new IllegalStateException("sensitive failure detail");
        }
    }
}
