package com.eu.habbo.habbohotel.rooms;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class RoomLoader {

    private final Operations operations;
    private final Supplier<Executor> workerExecutor;
    private final LongSupplier nanoTime;
    private final RoomLoadMetrics metrics;

    RoomLoader(
            Operations operations,
            Supplier<Executor> workerExecutor) {
        this(
                operations,
                workerExecutor,
                System::nanoTime,
                RoomLoadMetrics.flightRecorder());
    }

    RoomLoader(
            Operations operations,
            Supplier<Executor> workerExecutor,
            LongSupplier nanoTime,
            RoomLoadMetrics metrics) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.workerExecutor = Objects.requireNonNull(
                workerExecutor,
                "workerExecutor");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    void load(long generation) {
        if (!this.operations.prepare(generation)) {
            return;
        }

        long startedNanos = this.nanoTime.getAsLong();
        LoadAttempt attempt = new LoadAttempt();
        try {
            this.operations.initialize();
            this.operations.loadLayout();

            Executor executor = this.workerExecutor.get();
            CompletableFuture<Void> promotion = this.operations.shouldLoadPromotion()
                    ? run(this.operations::loadPromotion, executor)
                    : CompletableFuture.completedFuture(null);
            CompletableFuture<Void> items =
                    run(this.operations::loadItems, executor);
            CompletableFuture<Void> rights =
                    run(this.operations::loadRights, executor);
            CompletableFuture<Void> wordFilter =
                    run(this.operations::loadWordFilter, executor);
            CompletableFuture<Void> bots =
                    run(this.operations::loadBots, executor);
            CompletableFuture<Void> pets =
                    run(this.operations::loadPets, executor);

            try {
                items.join();
            } catch (Exception exception) {
                attempt.report(
                        "Error waiting for items to load",
                        exception);
            }

            CompletableFuture<Void> heightmap =
                    run(this.operations::loadHeightmap, executor);
            CompletableFuture<Void> wired =
                    run(this.operations::loadWiredData, executor);

            try {
                CompletableFuture.allOf(
                        promotion,
                        rights,
                        wordFilter,
                        bots,
                        pets,
                        heightmap,
                        wired).join();
            } catch (Exception exception) {
                attempt.report(
                        "Error waiting for parallel room data loading",
                        exception);
            }

            this.operations.resetIdleCycles();
        } catch (Exception exception) {
            attempt.report(
                    "Caught exception during room load",
                    exception);
        }

        boolean published = false;
        try {
            published = this.operations.finish(generation);
        } catch (RuntimeException exception) {
            attempt.failureCount++;
            throw exception;
        } finally {
            this.recordMeasurement(
                    generation,
                    startedNanos,
                    attempt.failureCount,
                    published);
        }
    }

    private static CompletableFuture<Void> run(
            Runnable operation,
            Executor executor) {
        return CompletableFuture.runAsync(operation, executor);
    }

    private void recordMeasurement(
            long generation,
            long startedNanos,
            int failureCount,
            boolean published) {
        long durationNanos = Math.max(
                0L,
                this.nanoTime.getAsLong() - startedNanos);
        try {
            this.metrics.record(new RoomLoadMeasurement(
                    this.operations.roomId(),
                    generation,
                    durationNanos,
                    failureCount,
                    published));
        } catch (RuntimeException exception) {
            this.operations.reportFailure(
                    "Caught exception recording room load metrics",
                    exception);
        }
    }

    private final class LoadAttempt {
        private int failureCount;

        private void report(String message, Exception exception) {
            this.failureCount++;
            RoomLoader.this.operations.reportFailure(message, exception);
        }
    }

    interface Operations {
        int roomId();

        boolean prepare(long generation);

        void initialize();

        void loadLayout();

        boolean shouldLoadPromotion();

        void loadPromotion();

        void loadItems();

        void loadRights();

        void loadWordFilter();

        void loadBots();

        void loadPets();

        void loadHeightmap();

        void loadWiredData();

        void resetIdleCycles();

        boolean finish(long generation);

        void reportFailure(String message, Exception exception);
    }
}
