package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DisconnectPersistenceGateTest {

    @Test
    void replacementSessionWaitsUntilQueuedPersistenceCompletes() {
        List<Runnable> queued = new ArrayList<>();
        DisconnectPersistenceGate gate = new DisconnectPersistenceGate(queued::add);
        DisconnectPersistenceGate.Registration registration = gate.begin(42);
        AtomicBoolean persisted = new AtomicBoolean();
        gate.submit(registration, () -> persisted.set(true));

        CompletableFuture<Boolean> waiting = CompletableFuture.supplyAsync(() -> gate.await(42));

        assertThrows(
                java.util.concurrent.TimeoutException.class,
                () -> waiting.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
        assertFalse(persisted.get());

        queued.removeFirst().run();

        assertTrue(waiting.join());
        assertTrue(persisted.get());
    }

    @Test
    void persistenceFailureStillReleasesReplacementSession() {
        List<Runnable> queued = new ArrayList<>();
        DisconnectPersistenceGate gate = new DisconnectPersistenceGate(queued::add);
        DisconnectPersistenceGate.Registration registration = gate.begin(42);
        gate.submit(registration, () -> {
            throw new IllegalStateException("expected");
        });

        queued.removeFirst().run();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertTrue(gate.await(42)));
    }

    @Test
    void cancelledDisconnectRemovesItsGateWithoutSchedulingWork() {
        List<Runnable> queued = new ArrayList<>();
        DisconnectPersistenceGate gate = new DisconnectPersistenceGate(queued::add);
        DisconnectPersistenceGate.Registration registration = gate.begin(42);

        gate.cancel(registration);

        assertTrue(gate.await(42));
        assertTrue(queued.isEmpty());
    }
}
