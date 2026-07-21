package com.eu.habbo.habbohotel.soundboard;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundboardCooldownGateTest {

    @Test
    void cooldownFollowsTheAccountAcrossRooms() {
        SoundboardCooldownGate gate = new SoundboardCooldownGate();

        assertTrue(gate.tryAcquire(42, 1_000L, 60).allowed());
        assertEquals(
                new SoundboardCooldownGate.Decision(false, 59),
                gate.tryAcquire(42, 2_000L, 60));
        assertTrue(gate.tryAcquire(42, 61_000L, 60).allowed());
    }

    @Test
    void cooldownsAreIndependentPerAccount() {
        SoundboardCooldownGate gate = new SoundboardCooldownGate();

        assertTrue(gate.tryAcquire(42, 1_000L, 60).allowed());
        assertTrue(gate.tryAcquire(43, 1_000L, 60).allowed());
    }

    @Test
    void zeroCooldownAlwaysAllows() {
        SoundboardCooldownGate gate = new SoundboardCooldownGate();

        assertTrue(gate.tryAcquire(42, 1_000L, 0).allowed());
        assertTrue(gate.tryAcquire(42, 1_000L, 0).allowed());
    }

    @Test
    void simultaneousRequestsAllowExactlyOne() throws Exception {
        SoundboardCooldownGate gate = new SoundboardCooldownGate();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> acquireTogether(gate, ready, start));
            Future<Boolean> second = executor.submit(() -> acquireTogether(gate, ready, start));

            ready.await();
            start.countDown();

            assertNotEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean acquireTogether(
            SoundboardCooldownGate gate,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return gate.tryAcquire(42, 1_000L, 60).allowed();
    }
}
