package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * With the queue full a caller waited on an unbounded put(): a slow database held every packet
 * thread and the hotel froze. At shutdown the queued tasks ran beside workers that might still be
 * busy, so an older write could land after a newer one.
 */
class PersistenceExecutorBoundsTest {

    @Test
    void aFullQueueRejectsAfterTheTimeoutInsteadOfHoldingTheCaller() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 1, 100, TimeUnit.MILLISECONDS);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                awaitUninterruptibly(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {});

            long startedAt = System.nanoTime();
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(waitedMillis >= 90, "the caller waits for room first: " + waitedMillis);
            assertTrue(waitedMillis < 2_000, "but not for as long as the database is slow: " + waitedMillis);
            assertEquals(1L, executor.operationSnapshot().rejectedCount());
        } finally {
            releaseWorker.countDown();
            executor.shutDown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void queuedWorkNeverRunsBesideAWorkerThatIsStillBusy() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 4);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean queuedRan = new AtomicBoolean();
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                awaitUninterruptibly(releaseWorker);
            });
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> queuedRan.set(true));

            executor.shutDown(100, TimeUnit.MILLISECONDS);

            assertFalse(queuedRan.get(), "the worker never stopped, so the queued write must not run beside it");
        } finally {
            releaseWorker.countDown();
        }
    }

    @Test
    void queuedWorkRunsOnceTheWorkersHaveStopped() throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor(1, 4);
        CountDownLatch workerStarted = new CountDownLatch(1);
        AtomicBoolean queuedRan = new AtomicBoolean();
        executor.execute(() -> {
            workerStarted.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
                // shutdownNow interrupts the worker; it stops here
            }
        });
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
        executor.execute(() -> queuedRan.set(true));

        executor.shutDown(100, TimeUnit.MILLISECONDS);

        assertTrue(queuedRan.get(), "an accepted write still runs once nothing else can");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try {
                if (latch.await(100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException ignored) {
                // a worker that does not stop when interrupted, like one stuck in a driver call
            }
        }
    }
}
