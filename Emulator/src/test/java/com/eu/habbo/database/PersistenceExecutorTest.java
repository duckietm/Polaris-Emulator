package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
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
    void saturationQueuesWorkWithoutExecutingJdbcOnCaller() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch saturatedTaskCompleted = new CountDownLatch(1);
        AtomicReference<String> saturatedTaskThread = new AtomicReference<>();
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {});

            Thread submitter = Thread.ofPlatform()
                    .name("persistence-submitter")
                    .start(() -> executor.execute(() -> {
                        saturatedTaskThread.set(Thread.currentThread().getName());
                        saturatedTaskCompleted.countDown();
                    }));

            assertFalse(saturatedTaskCompleted.await(100, TimeUnit.MILLISECONDS));
            releaseWorker.countDown();
            submitter.join(2_000);

            assertFalse(submitter.isAlive());
            assertTrue(saturatedTaskCompleted.await(2, TimeUnit.SECONDS));
            assertTrue(saturatedTaskThread.get().startsWith("Polaris-JDBC-"));
            PersistenceExecutor.Metrics metrics = executor.metrics();
            assertEquals(1, metrics.queueCapacity());
            assertEquals(1, metrics.highWaterMark());
            assertEquals(1L, metrics.saturationCount());
            assertTrue(metrics.totalSubmissionWaitNanos() > 0L);

        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void workSubmittedAfterShutdownIsRejectedWithoutRunningOnCaller() {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        executor.shutDown(2, TimeUnit.SECONDS);
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> ran.set(true)));

        assertFalse(ran.get());
        assertFalse(executor.metrics().accepting());
    }

    @Test
    void nestedPersistenceSubmissionDoesNotDeadlockWhenQueueIsFull() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        CountDownLatch outerCompleted = new CountDownLatch(1);
        CountDownLatch nestedCompleted = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                executor.execute(() -> {});
                executor.execute(nestedCompleted::countDown);
                outerCompleted.countDown();
            });

            assertTrue(outerCompleted.await(1, TimeUnit.SECONDS));
            assertTrue(nestedCompleted.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void interruptedSubmissionIsRejectedAndPreservesInterruptStatus() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicReference<Throwable> rejection = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {});

            Thread submitter = Thread.ofPlatform().start(() -> {
                try {
                    executor.execute(() -> {});
                } catch (RejectedExecutionException exception) {
                    rejection.set(exception);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (executor.metrics().saturationCount() == 0L && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(1L, executor.metrics().saturationCount());

            submitter.interrupt();
            submitter.join(2_000);

            assertFalse(submitter.isAlive());
            assertTrue(rejection.get() instanceof RejectedExecutionException);
            assertTrue(interrupted.get());
        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void runtimeFactoryHonorsConfiguredQueueCapacity() {
        PersistenceExecutor executor = PersistenceExecutor.forRuntimeThreads(8, 4_096);
        try {
            assertEquals(4_096, executor.metrics().queueCapacity());
        } finally {
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
