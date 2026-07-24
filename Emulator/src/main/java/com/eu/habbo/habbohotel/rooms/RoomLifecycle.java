package com.eu.habbo.habbohotel.rooms;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class RoomLifecycle {

    private final Object lock = new Object();
    private final CycleTaskSlot cycleTask;
    private State state = State.PRELOADED;
    private long generation;
    private long activeLoadGeneration;
    private boolean loaded;
    private boolean preloaded = true;
    private boolean loading;
    private int idleCycles;
    private CompletableFuture<Void> loadingFuture;

    RoomLifecycle(CycleTaskSlot cycleTask) {
        this.cycleTask = Objects.requireNonNull(cycleTask, "cycleTask");
    }

    boolean isLoading() {
        synchronized (this.lock) {
            return this.loading;
        }
    }

    boolean isLoadedOrLoading() {
        synchronized (this.lock) {
            return this.loaded || this.loading;
        }
    }

    boolean isLoaded() {
        synchronized (this.lock) {
            return this.loaded;
        }
    }

    boolean isPreloaded() {
        synchronized (this.lock) {
            return this.preloaded;
        }
    }

    long generation() {
        synchronized (this.lock) {
            return this.generation;
        }
    }

    long beginLoad() {
        synchronized (this.lock) {
            return this.beginLoadLocked();
        }
    }

    private long beginLoadLocked() {
        if (this.loaded || this.loading || !this.preloaded || this.state == State.DISPOSING) {
            return -1L;
        }

        this.loading = true;
        this.state = State.LOADING;
        long loadGeneration = ++this.generation;
        this.activeLoadGeneration = loadGeneration;
        return loadGeneration;
    }

    boolean prepareLoad(long loadGeneration) {
        synchronized (this.lock) {
            if (loadGeneration != this.generation || this.state != State.LOADING) {
                return false;
            }

            this.preloaded = false;
            return true;
        }
    }

    boolean publishLoad(long loadGeneration, Supplier<ScheduledFuture<?>> cycleScheduler) {
        synchronized (this.lock) {
            if (loadGeneration != this.generation
                    || loadGeneration != this.activeLoadGeneration
                    || this.state != State.LOADING) {
                this.completeLoadAttemptLocked(loadGeneration);
                return false;
            }

            ScheduledFuture<?> previousTask = this.cycleTask.get();
            if (previousTask != null) {
                previousTask.cancel(false);
            }

            ScheduledFuture<?> newTask = cycleScheduler.get();
            if (newTask == null) {
                this.failLoadLocked(loadGeneration);
                return false;
            }

            this.cycleTask.set(newTask);
            this.loaded = true;
            this.preloaded = false;
            this.loading = false;
            this.loadingFuture = null;
            this.activeLoadGeneration = 0L;
            this.state = State.LOADED;
            return true;
        }
    }

    boolean beginUnload() {
        synchronized (this.lock) {
            if (this.state == State.DISPOSING) {
                return false;
            }

            boolean loadInProgress = this.activeLoadGeneration != 0L;
            this.generation++;
            this.state = State.DISPOSING;
            this.loaded = false;

            ScheduledFuture<?> task = this.cycleTask.get();
            this.cycleTask.set(null);
            if (task != null) {
                task.cancel(false);
            }

            if (!loadInProgress) {
                this.loading = false;
                this.loadingFuture = null;
            }
            return true;
        }
    }

    void finishUnload() {
        synchronized (this.lock) {
            this.preloaded = true;
            this.state = State.PRELOADED;
        }
    }

    void quiesceCycle() {
        synchronized (this.lock) {
            ScheduledFuture<?> task = this.cycleTask.get();
            this.cycleTask.set(null);
            if (task != null) {
                task.cancel(false);
            }
        }
    }

    void resetIdleCycles() {
        this.idleCycles = 0;
    }

    boolean advanceIdleUnload(boolean empty) {
        if (!empty) {
            this.idleCycles = 0;
            return false;
        }

        if (this.idleCycles < 60) {
            this.idleCycles++;
            return false;
        }

        return true;
    }

    void dispose(boolean prevented, BooleanSupplier cancellationCheck, Consumer<Boolean> cleanup, Runnable completion) {
        if (prevented || cancellationCheck.getAsBoolean()) {
            return;
        }

        boolean wasLoaded = this.isLoaded();
        if (!this.beginUnload()) {
            return;
        }

        try {
            cleanup.accept(wasLoaded);
        } finally {
            this.finishUnload();
        }

        completion.run();
    }

    void failLoad(long loadGeneration) {
        synchronized (this.lock) {
            this.failLoadLocked(loadGeneration);
        }
    }

    private void failLoadLocked(long loadGeneration) {
        if (loadGeneration != this.activeLoadGeneration) {
            return;
        }

        if (loadGeneration == this.generation && this.state == State.LOADING) {
            this.preloaded = true;
            this.state = State.PRELOADED;
        }
        this.completeLoadAttemptLocked(loadGeneration);
    }

    private void completeLoadAttemptLocked(long loadGeneration) {
        if (loadGeneration == this.activeLoadGeneration) {
            this.activeLoadGeneration = 0L;
            this.loading = false;
            this.loadingFuture = null;
        }
    }

    void startBackgroundLoad(Executor executor, LongConsumer loadOperation) {
        synchronized (this.lock) {
            long loadGeneration = this.beginLoadLocked();
            if (loadGeneration < 0L) {
                return;
            }

            try {
                this.loadingFuture = CompletableFuture.runAsync(() -> loadOperation.accept(loadGeneration), executor);
            } catch (RuntimeException exception) {
                this.failLoadLocked(loadGeneration);
                throw exception;
            }
        }
    }

    CompletableFuture<Void> loadingFuture() {
        synchronized (this.lock) {
            return this.loadingFuture;
        }
    }

    LoadAttempt beginOrJoinLoad() {
        synchronized (this.lock) {
            if (this.loading) {
                return new LoadAttempt(this.loadingFuture, -1L);
            }

            return new LoadAttempt(null, this.beginLoadLocked());
        }
    }

    record LoadAttempt(CompletableFuture<Void> future, long generation) {}

    interface CycleTaskSlot {

        ScheduledFuture<?> get();

        void set(ScheduledFuture<?> task);
    }

    private enum State {
        PRELOADED,
        LOADING,
        LOADED,
        DISPOSING
    }
}
