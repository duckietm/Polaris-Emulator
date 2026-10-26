package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Internal owner of delayed WIRED admission, timing and room/actor lifecycle checks. */
final class WiredDelayedScheduler {
    private static final int TASK_PENDING = 0;
    private static final int TASK_RUNNING = 1;
    private static final int TASK_COMPLETED = 2;
    private static final int TASK_CANCELLED = 3;

    @FunctionalInterface
    interface ScheduledTask {
        boolean cancel();
    }

    @FunctionalInterface
    interface TaskScheduler {
        ScheduledTask schedule(Runnable task, long delayMs);
    }

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    @FunctionalInterface
    interface RoomResolver {
        Room resolve(int roomId);
    }

    @FunctionalInterface
    interface ResolvedWork {
        void run(WiredDelayedExecutionSnapshot.Resolved resolved);
    }

    private final TaskScheduler taskScheduler;
    private final LongSupplier clock;
    private final DiagnosticSink diagnostics;
    private final RoomResolver roomResolver;
    private final Set<PendingTask> pendingTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    WiredDelayedScheduler(
            TaskScheduler taskScheduler, LongSupplier clock, DiagnosticSink diagnostics, RoomResolver roomResolver) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.roomResolver = Objects.requireNonNull(roomResolver, "roomResolver");
    }

    void scheduleEffect(
            IWiredEffect effect,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            Runnable work) {
        scheduleEffect(
                effect, context, delay, triggerTime, roomDiagnostics, sourceLabel, sourceId, ignored -> work.run());
    }

    void scheduleEffect(
            IWiredEffect effect,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            ResolvedWork work) {
        String reason = String.format(
                "Scheduling delayed effect %s with delay %d tick(s)",
                effect.getClass().getSimpleName(), delay);
        String blockedMessage = "Delayed events cap blocked effect {}";
        schedule(
                List.of(effect),
                context,
                delay,
                triggerTime,
                roomDiagnostics,
                sourceLabel,
                sourceId,
                reason,
                blockedMessage,
                new Object[] {effect.getClass().getSimpleName()},
                work);
    }

    void scheduleOrderedBatch(
            List<IWiredEffect> batch,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            Runnable work) {
        scheduleOrderedBatch(
                batch, context, delay, triggerTime, roomDiagnostics, sourceLabel, sourceId, ignored -> work.run());
    }

    void scheduleOrderedBatch(
            List<IWiredEffect> batch,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            ResolvedWork work) {
        String reason =
                String.format("Scheduling ordered batch with %d effect(s) and delay %d tick(s)", batch.size(), delay);
        schedule(
                batch,
                context,
                delay,
                triggerTime,
                roomDiagnostics,
                sourceLabel,
                sourceId,
                reason,
                "Delayed events cap blocked ordered batch with {} effect(s)",
                new Object[] {batch.size()},
                work);
    }

    private void schedule(
            List<IWiredEffect> effects,
            WiredContext context,
            int delay,
            long triggerTime,
            WiredRoomDiagnostics roomDiagnostics,
            String sourceLabel,
            int sourceId,
            String reason,
            String blockedMessage,
            Object[] blockedArguments,
            ResolvedWork work) {
        WiredDelayedExecutionSnapshot snapshot = WiredDelayedExecutionSnapshot.capture(effects, context, triggerTime);
        if (!roomDiagnostics.tryScheduleDelayedEvent(this.clock.getAsLong(), sourceLabel, sourceId, reason)) {
            this.diagnostics.log(context.room(), blockedMessage, blockedArguments);
            return;
        }

        long delayMs = WiredDelay.toMillis(delay);
        long elapsedSinceTrigger = Math.max(0L, this.clock.getAsLong() - snapshot.triggerTime());
        long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);

        int roomId = snapshot.roomId();
        long pausedAtSchedule = WiredDisablerPauses.pausedMs(roomId);
        long dueAt = this.clock.getAsLong() + remainingDelayMs;

        DelayedStep delayedWork = () -> {
            // Paused by a Wired Disabler: the delay waits it out and ends later by the time it was paused, so one that
            // would have ended during the pause does not run the moment the disabler goes off.
            if (WiredDisablerPauses.isPaused(roomId)) {
                return WiredDisablerPauses.ROOM_TICK_MS;
            }
            long paused = WiredDisablerPauses.pausedMs(roomId) - pausedAtSchedule;
            if (paused > 0) {
                long now = this.clock.getAsLong();
                if (now < dueAt + paused) {
                    return dueAt + paused - now;
                }
            }

            Room room = null;
            try {
                room = this.roomResolver.resolve(snapshot.roomId());
            } catch (RuntimeException ignored) {
                // Tests and pre-index programmatic contexts can have no global room manager.
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_ROOM_RESOLVE,
                        snapshot.roomId(),
                        sourceId,
                        ignored);
            }
            // The chain keeps running when its user left or the room emptied; only an unload ends it.
            if (room == null || !room.isLoaded()) {
                return DONE;
            }
            var resolved = snapshot.resolve(room);
            if (resolved.isEmpty()) {
                return DONE;
            }
            WiredDelayedExecutionSnapshot.Resolved runnable = withoutActorWork(resolved.get());
            if (runnable.effects().isEmpty()) {
                return DONE;
            }

            work.run(runnable);
            return DONE;
        };

        PendingTask pendingTask = new PendingTask(delayedWork, roomDiagnostics);
        this.pendingTasks.add(pendingTask);

        try {
            if (!this.accepting.get()) {
                pendingTask.cancel();
                this.diagnostics.log(context.room(), "Delayed scheduler rejected work during shutdown");
                return;
            }

            ScheduledTask scheduledTask = this.book(pendingTask, remainingDelayMs);
            if (scheduledTask == null) {
                pendingTask.cancel();
                this.diagnostics.log(context.room(), "Delayed scheduler rejected work during shutdown");
                return;
            }

            pendingTask.attach(scheduledTask);
            if (!this.accepting.get()) {
                pendingTask.cancel();
            }
        } catch (RuntimeException exception) {
            pendingTask.cancel();
            this.diagnostics.log(
                    context.room(),
                    "Delayed scheduler failed to accept work: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * The effects that can still run: one that needs the user who left resolves to nobody and does nothing, the
     * rest of its chain still runs.
     */
    private static WiredDelayedExecutionSnapshot.Resolved withoutActorWork(
            WiredDelayedExecutionSnapshot.Resolved resolved) {
        if (resolved.context().hasActor()) {
            return resolved;
        }
        List<IWiredEffect> runnable = new ArrayList<>(resolved.effects().size());
        for (IWiredEffect effect : resolved.effects()) {
            if (effect != null && !effect.requiresActor()) {
                runnable.add(effect);
            }
        }
        return runnable.size() == resolved.effects().size()
                ? resolved
                : new WiredDelayedExecutionSnapshot.Resolved(resolved.context(), List.copyOf(runnable));
    }

    /** One wake of a delayed task: {@link #DONE}, or the milliseconds to wait before it wakes again. */
    @FunctionalInterface
    private interface DelayedStep {
        long run();
    }

    private static final long DONE = -1L;

    /** The one place a delayed task is handed to the executor: its first wake and every wake after a pause. */
    private ScheduledTask book(PendingTask task, long delayMs) {
        return this.taskScheduler.schedule(task::run, delayMs);
    }

    int shutdown() {
        this.accepting.set(false);
        int cancelled = 0;

        for (PendingTask pendingTask : List.copyOf(this.pendingTasks)) {
            if (pendingTask.cancel()) {
                cancelled++;
            }
        }

        return cancelled;
    }

    private final class PendingTask implements Runnable {
        private final DelayedStep work;
        private final WiredRoomDiagnostics roomDiagnostics;
        private final AtomicInteger state = new AtomicInteger(TASK_PENDING);
        private final AtomicReference<ScheduledTask> scheduledTask = new AtomicReference<>();

        private PendingTask(DelayedStep work, WiredRoomDiagnostics roomDiagnostics) {
            this.work = work;
            this.roomDiagnostics = roomDiagnostics;
        }

        private void attach(ScheduledTask task) {
            this.scheduledTask.set(task);

            if (this.state.get() != TASK_PENDING) {
                cancelQuietly(task);
            }
        }

        @Override
        public void run() {
            if (!this.state.compareAndSet(TASK_PENDING, TASK_RUNNING)) {
                return;
            }

            long again = DONE;
            try {
                again = this.work.run();
            } finally {
                if (again < 0 || !this.rebook(again)) {
                    pendingTasks.remove(this);
                    this.state.set(TASK_COMPLETED);
                    this.roomDiagnostics.completeDelayedEvent();
                }
            }
        }

        /** Books the next wake of a paused delay; false when the scheduler no longer takes work. */
        private boolean rebook(long delayMs) {
            if (!accepting.get()) {
                return false;
            }
            this.state.set(TASK_PENDING);
            try {
                ScheduledTask next = book(this, delayMs);
                if (next == null) {
                    this.state.set(TASK_RUNNING);
                    return false;
                }
                this.attach(next);
                return true;
            } catch (RuntimeException exception) {
                this.state.set(TASK_RUNNING);
                return false;
            }
        }

        private boolean cancel() {
            if (!this.state.compareAndSet(TASK_PENDING, TASK_CANCELLED)) {
                return false;
            }

            ScheduledTask task = this.scheduledTask.get();
            if (task != null) {
                cancelQuietly(task);
            }
            pendingTasks.remove(this);
            this.roomDiagnostics.completeDelayedEvent();
            return true;
        }

        private static void cancelQuietly(ScheduledTask task) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
                // Executor cancellation must not strand diagnostics or prevent runtime cleanup.
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.DELAYED_TASK_CANCEL, ignored);
            }
        }
    }
}
