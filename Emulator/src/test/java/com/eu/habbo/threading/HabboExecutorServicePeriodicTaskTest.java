package com.eu.habbo.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HabboExecutorServicePeriodicTaskTest {
    @Test
    void periodicTaskContinuesAfterOneExecutionThrows() throws Exception {
        HabboExecutorService executor = new HabboExecutorService(1, Executors.defaultThreadFactory());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch recoveredExecution = new CountDownLatch(1);

        try {
            executor.scheduleAtFixedRate(() -> {
                if (executions.incrementAndGet() == 1) {
                    throw new IllegalStateException("first execution fails");
                }
                recoveredExecution.countDown();
            }, 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(recoveredExecution.await(2, TimeUnit.SECONDS),
                    "an exception must not permanently cancel a periodic emulator task");
        } finally {
            executor.shutdownNow();
        }
    }
}
