package com.eu.habbo.habbohotel.users;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DisconnectPersistenceGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisconnectPersistenceGate.class);

    private final Executor executor;
    private final ConcurrentHashMap<Integer, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

    DisconnectPersistenceGate(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    Registration begin(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (this.pending.putIfAbsent(userId, completion) != null) {
            throw new IllegalStateException("disconnect persistence already pending for user " + userId);
        }
        return new Registration(userId, completion);
    }

    void submit(Registration registration, Runnable persistence) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(persistence, "persistence");
        Runnable guarded = () -> {
            try {
                persistence.run();
            } catch (Exception exception) {
                LOGGER.error("Disconnect persistence failed for user {}", registration.userId(), exception);
            } finally {
                this.complete(registration);
            }
        };
        try {
            this.executor.execute(guarded);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unable to queue disconnect persistence for user {}; running inline",
                    registration.userId(),
                    exception);
            guarded.run();
        }
    }

    void cancel(Registration registration) {
        Objects.requireNonNull(registration, "registration");
        this.complete(registration);
    }

    boolean await(int userId) {
        CompletableFuture<Void> completion = this.pending.get(userId);
        if (completion == null) {
            return true;
        }
        try {
            completion.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException exception) {
            return true;
        }
    }

    private void complete(Registration registration) {
        this.pending.remove(registration.userId(), registration.completion());
        registration.completion().complete(null);
    }

    record Registration(int userId, CompletableFuture<Void> completion) {}
}
