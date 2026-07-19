package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetMailExecutorTest {

    @Test
    void resetMailUsesTheDedicatedAuthWorkerPool()
            throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> workerName =
                new AtomicReference<>();

        assertTrue(SessionEndpoints.submitResetEmail(() -> {
            workerName.set(Thread.currentThread().getName());
            completed.countDown();
        }));

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertTrue(workerName.get().startsWith("auth-http-worker-"));
    }
}
