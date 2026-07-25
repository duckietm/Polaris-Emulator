package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredDelayedSchedulerTest {

    @Test
    void preservesFiveHundredMillisecondTimingAndCompletionAccounting() {
        Room room = occupiedRoom(71);
        WiredContext context = context(room, null);
        IWiredEffect effect = ignored -> {};
        WiredRoomDiagnostics diagnostics = diagnostics();
        AtomicLong clock = new AtomicLong(1_200L);
        List<Runnable> tasks = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    delays.add(delayMs);
                    return () -> true;
                },
                clock::get,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleOrderedBatch(
                List.of(effect), context, 2, 1_000L, diagnostics, "fixture", 7, executions::incrementAndGet);

        assertEquals(List.of(800L), delays);
        assertEquals(1, pending(diagnostics, clock.get()));
        tasks.getFirst().run();
        assertEquals(1, executions.get());
        assertEquals(0, pending(diagnostics, clock.get()));
    }

    @Test
    void revalidatesActorBeforeRunningActorRequiredWork() {
        Room room = occupiedRoom(72);
        RoomUnit actor = mock(RoomUnit.class);
        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        WiredContext context = context(room, actor);
        IWiredEffect actorRequired = new IWiredEffect() {
            @Override
            public void execute(WiredContext ignored) {}

            @Override
            public boolean requiresActor() {
                return true;
            }
        };
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 2_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(
                actorRequired, context, 1, 2_000L, diagnostics, "fixture", 8, executions::incrementAndGet);

        when(room.getRoomUnits()).thenReturn(Set.of());
        tasks.getFirst().run();

        assertEquals(0, executions.get());
        assertEquals(0, pending(diagnostics, 2_000L));
    }

    @Test
    void dropsWorkWhenTheRoomReloadsBeforeItsDelayExpires() {
        Room room = occupiedRoom(73);
        AtomicLong generation = new AtomicLong(11L);
        when(room.getLifecycleGeneration()).thenAnswer(ignored -> generation.get());
        WiredContext context = context(room, null);
        IWiredEffect effect = ignored -> {};
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 3_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(effect, context, 1, 3_000L, diagnostics, "fixture", 9, executions::incrementAndGet);

        generation.incrementAndGet();
        tasks.getFirst().run();

        assertEquals(0, executions.get());
        assertEquals(0, pending(diagnostics, 3_000L));
    }

    @Test
    void dropsWorkCapturedBeforeTheRoomHasAPublishedLifecycleGeneration() {
        Room room = occupiedRoom(730);
        when(room.getLifecycleGeneration()).thenReturn(0L);
        WiredContext context = context(room, null);
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 3_500L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(
                ignored -> {}, context, 1, 3_500L, diagnostics, "fixture", 90, executions::incrementAndGet);
        tasks.getFirst().run();

        assertEquals(0, executions.get());
        assertEquals(0, pending(diagnostics, 3_500L));
    }

    @Test
    void delayedTargetsAreAnImmutableScheduleTimeSnapshot() {
        Room room = occupiedRoom(74);
        RoomUnit scheduledTarget = mock(RoomUnit.class);
        RoomUnit laterTarget = mock(RoomUnit.class);
        when(scheduledTarget.getId()).thenReturn(501);
        when(laterTarget.getId()).thenReturn(502);
        when(room.getRoomUnits()).thenReturn(Set.of(scheduledTarget, laterTarget));
        WiredContext context = context(room, scheduledTarget);
        IWiredEffect effect = ignored -> {};
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger scheduledTargetsSeen = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 4_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(
                effect,
                context,
                1,
                4_000L,
                diagnostics,
                "fixture",
                10,
                resolved -> scheduledTargetsSeen.set(
                        resolved.context().targets().users().contains(scheduledTarget) ? 1 : 0));

        context.targets().setUsers(List.of(laterTarget));
        tasks.getFirst().run();

        assertEquals(1, scheduledTargetsSeen.get());
        assertEquals(0, pending(diagnostics, 4_000L));
    }

    @Test
    void dropsAFirstPartyEffectWhenItsFurnitureIdIsReused() {
        Room room = occupiedRoom(75);
        when(room.getLifecycleGeneration()).thenReturn(31L);
        InteractionWiredEffect scheduledEffect = mock(InteractionWiredEffect.class);
        when(scheduledEffect.getId()).thenReturn(901);
        AtomicLong incarnation = new AtomicLong(41L);
        AtomicReference<HabboItem> liveItem = new AtomicReference<>(scheduledEffect);
        when(room.getItemIncarnation(901)).thenAnswer(ignored -> incarnation.get());
        when(room.getHabboItem(901)).thenAnswer(ignored -> liveItem.get());
        WiredContext context = context(room, null);
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 5_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(
                scheduledEffect,
                context,
                1,
                5_000L,
                diagnostics,
                "fixture",
                11,
                ignored -> executions.incrementAndGet());

        InteractionWiredEffect replacement = mock(InteractionWiredEffect.class);
        when(replacement.getId()).thenReturn(901);
        liveItem.set(replacement);
        incarnation.incrementAndGet();
        tasks.getFirst().run();

        assertEquals(0, executions.get());
        assertEquals(0, pending(diagnostics, 5_000L));
    }

    @Test
    void contextVariablesAreCopiedAtScheduleTime() {
        Room room = occupiedRoom(76);
        WiredContext context = context(room, null);
        context.contextVariables().assignValue(601, 7, true);
        IWiredEffect effect = ignored -> {};
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicReference<Integer> delayedValue = new AtomicReference<>();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> true;
                },
                () -> 6_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(
                effect,
                context,
                1,
                6_000L,
                diagnostics,
                "fixture",
                12,
                resolved ->
                        delayedValue.set(resolved.context().contextVariables().getValue(601)));

        context.contextVariables().updateValue(601, 9);
        tasks.getFirst().run();

        assertEquals(7, delayedValue.get());
        assertEquals(0, pending(diagnostics, 6_000L));
    }

    @Test
    void shutdownCancelsAcceptedWorkAndReleasesItsReservationExactlyOnce() {
        Room room = occupiedRoom(77);
        WiredContext context = context(room, null);
        IWiredEffect effect = ignored -> {};
        WiredRoomDiagnostics diagnostics = diagnostics();
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger cancellationCalls = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    tasks.add(task);
                    return () -> {
                        cancellationCalls.incrementAndGet();
                        return true;
                    };
                },
                () -> 7_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(effect, context, 10, 7_000L, diagnostics, "fixture", 13, executions::incrementAndGet);

        assertEquals(1, pending(diagnostics, 7_000L));
        assertEquals(1, scheduler.shutdown());
        assertEquals(1, cancellationCalls.get());
        assertEquals(0, pending(diagnostics, 7_000L));

        tasks.getFirst().run();
        assertEquals(0, executions.get());
        assertEquals(0, pending(diagnostics, 7_000L));
        assertEquals(0, scheduler.shutdown());

        scheduler.scheduleEffect(effect, context, 10, 7_000L, diagnostics, "fixture", 14, executions::incrementAndGet);
        assertEquals(0, pending(diagnostics, 7_000L));
        assertEquals(1, tasks.size());
    }

    @Test
    void cancellationFailureCannotStrandDelayedAccounting() {
        Room room = occupiedRoom(78);
        WiredContext context = context(room, null);
        WiredRoomDiagnostics diagnostics = diagnostics();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> () -> {
                    throw new IllegalStateException("fixture-cancel-failure");
                },
                () -> 8_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);

        scheduler.scheduleEffect(ignored -> {}, context, 10, 8_000L, diagnostics, "fixture", 15, () -> {});

        assertEquals(1, pending(diagnostics, 8_000L));
        assertEquals(1, scheduler.shutdown());
        assertEquals(0, pending(diagnostics, 8_000L));
    }

    @Test
    void shutdownWinsWhenSchedulingIsStillPublishingItsCancellationHandle() throws Exception {
        Room room = occupiedRoom(79);
        WiredContext context = context(room, null);
        WiredRoomDiagnostics diagnostics = diagnostics();
        CountDownLatch schedulerEntered = new CountDownLatch(1);
        CountDownLatch allowPublication = new CountDownLatch(1);
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        WiredDelayedScheduler scheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    queuedTask.set(task);
                    schedulerEntered.countDown();
                    try {
                        if (!allowPublication.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("fixture publication timeout");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return () -> {
                        cancellations.incrementAndGet();
                        return true;
                    };
                },
                () -> 9_000L,
                (ignoredRoom, format, arguments) -> {},
                ignoredId -> room);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> scheduleCall = executor.submit(() -> scheduler.scheduleEffect(
                    ignored -> {}, context, 10, 9_000L, diagnostics, "fixture", 16, executions::incrementAndGet));

            assertTrue(schedulerEntered.await(2, TimeUnit.SECONDS));
            assertEquals(1, scheduler.shutdown());
            allowPublication.countDown();
            scheduleCall.get(2, TimeUnit.SECONDS);

            assertEquals(1, cancellations.get());
            assertEquals(0, pending(diagnostics, 9_000L));
            queuedTask.get().run();
            assertEquals(0, executions.get());
        } finally {
            allowPublication.countDown();
            executor.shutdownNow();
        }
    }

    private static WiredContext context(Room room, RoomUnit actor) {
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.CUSTOM, room);
        if (actor != null) {
            builder.actor(actor);
        }
        return new WiredContext(builder.build(), null, mock(WiredServices.class), new WiredState(10));
    }

    private static Room occupiedRoom(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        when(room.getHabbos()).thenReturn(List.of(mock(Habbo.class)));
        when(room.getRoomUnits()).thenReturn(Set.of());
        return room;
    }

    private static WiredRoomDiagnostics diagnostics() {
        return new WiredRoomDiagnostics(1_000, 100, 10, 50, 150, 70, 5);
    }

    private static int pending(WiredRoomDiagnostics diagnostics, long now) {
        return diagnostics.snapshot(0, 10, 0L, now).getDelayedEventsPending();
    }
}
