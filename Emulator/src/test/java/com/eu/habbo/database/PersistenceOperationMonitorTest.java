package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PersistenceOperationMonitorTest {

    @Test
    void successfulOperationUpdatesCountersAndDuration() {
        AtomicLong nanoTime = new AtomicLong(100L);
        AtomicLong epochMillis = new AtomicLong(1_000L);
        PersistenceOperationMonitor monitor = new PersistenceOperationMonitor(4, nanoTime::get, epochMillis::get);

        PersistenceOperationMonitor.Operation operation = monitor.started("inventory.delete");
        nanoTime.set(350L);
        monitor.succeeded(operation);

        PersistenceOperationMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(1L, snapshot.submittedCount());
        assertEquals(1L, snapshot.succeededCount());
        assertEquals(0L, snapshot.failedCount());
        assertEquals(0L, snapshot.rejectedCount());
        assertEquals(0L, snapshot.activeCount());
        assertEquals(250L, snapshot.totalDurationNanos());
        assertEquals(250L, snapshot.maxDurationNanos());
        assertEquals(0, snapshot.recentFailures().size());
    }

    @Test
    void recentFailuresAreBoundedNewestFirstAndExcludeExceptionMessages() {
        AtomicLong nanoTime = new AtomicLong(100L);
        AtomicLong epochMillis = new AtomicLong(1_000L);
        PersistenceOperationMonitor monitor = new PersistenceOperationMonitor(2, nanoTime::get, epochMillis::get);

        fail(monitor, nanoTime, "first", new IllegalStateException("secret-one"));
        fail(monitor, nanoTime, "second", new IllegalArgumentException("secret-two"));
        fail(monitor, nanoTime, "third", new UnsupportedOperationException("secret-three"));

        PersistenceOperationMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(3L, snapshot.submittedCount());
        assertEquals(3L, snapshot.failedCount());
        assertEquals(2, snapshot.recentFailures().size());
        assertEquals("third", snapshot.recentFailures().get(0).operationType());
        assertEquals(
                "UnsupportedOperationException",
                snapshot.recentFailures().get(0).errorType());
        assertEquals("second", snapshot.recentFailures().get(1).operationType());
    }

    @Test
    void rejectedOperationHasAUniqueIdAndLeavesNoActiveWork() {
        AtomicLong nanoTime = new AtomicLong(100L);
        AtomicLong epochMillis = new AtomicLong(1_000L);
        PersistenceOperationMonitor monitor = new PersistenceOperationMonitor(2, nanoTime::get, epochMillis::get);

        PersistenceOperationMonitor.Operation first = monitor.started("first");
        monitor.succeeded(first);
        PersistenceOperationMonitor.Operation rejected = monitor.started("second");
        nanoTime.set(150L);
        monitor.rejected(rejected, new IllegalStateException("not accepted"));

        PersistenceOperationMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(1L, first.operationId());
        assertEquals(2L, rejected.operationId());
        assertEquals(1L, snapshot.rejectedCount());
        assertEquals(0L, snapshot.activeCount());
        assertEquals("REJECTED", snapshot.recentFailures().get(0).outcome());
    }

    private static void fail(
            PersistenceOperationMonitor monitor, AtomicLong nanoTime, String type, RuntimeException failure) {
        PersistenceOperationMonitor.Operation operation = monitor.started(type);
        nanoTime.addAndGet(10L);
        monitor.failed(operation, failure);
    }
}
