package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.threading.ThreadPooling;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WiredDelayedLifecycleCompatibilityTest {

    private ThreadPooling previousThreading;
    private ThreadPooling scheduler;

    @BeforeEach
    void installCapturedScheduler() throws Exception {
        Field field = Emulator.class.getDeclaredField("threading");
        field.setAccessible(true);
        previousThreading = (ThreadPooling) field.get(null);
        scheduler = mock(ThreadPooling.class);
        when(scheduler.run(any(Runnable.class), anyLong())).thenReturn(mock(ScheduledFuture.class));
        field.set(null, scheduler);
    }

    @AfterEach
    void restoreScheduler() throws Exception {
        Field field = Emulator.class.getDeclaredField("threading");
        field.setAccessible(true);
        field.set(null, previousThreading);
    }

    @Test
    void delayUsesFiveHundredMillisecondTicksAndCompletesDiagnostics() {
        Room room = room(51, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        assertEquals(1, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());

        Runnable scheduled = scheduledTask();
        scheduled.run();

        assertEquals(1, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void roomUnloadDropsCapturedDelayedWork() {
        Room room = room(52, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        when(room.isLoaded()).thenReturn(false);
        scheduledTask().run();

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void emptyRoomDropsCapturedDelayedWork() {
        Room room = room(53, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));

        assertTrue(engine.handleEvent(event(room, null)));
        when(room.getHabbos()).thenReturn(List.of());
        scheduledTask().run();

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void actorRequiredDelayedEffectIsDroppedAfterActorLeaves() {
        Room room = room(54, true, true);
        RoomUnit actor = mock(RoomUnit.class);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, true));

        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        assertTrue(engine.handleEvent(event(room, actor)));
        when(room.getRoomUnits()).thenReturn(Set.of());
        scheduledTask().run();

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    @Test
    void actorIndependentDelayedEffectExecutesWithoutExposingDepartedActorOrTarget() {
        Room room = room(55, true, true);
        RoomUnit actor = mock(RoomUnit.class);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger visibleActors = new AtomicInteger();
        IWiredEffect effect = new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {
                executions.incrementAndGet();
                visibleActors.addAndGet(context.actor().isPresent() ? 1 : 0);
                visibleActors.addAndGet(context.targets().users().contains(actor) ? 1 : 0);
            }

            @Override
            public int getDelay() {
                return 1;
            }
        };
        WiredEngine engine = engine(room, effect);

        when(room.getRoomUnits()).thenReturn(Set.of(actor));
        assertTrue(engine.handleEvent(event(room, actor)));
        when(room.getRoomUnits()).thenReturn(Set.of());
        scheduledTask().run();

        assertEquals(1, executions.get());
        assertEquals(0, visibleActors.get());
    }

    @Test
    void schedulerRejectionReleasesDiagnosticsReservation() {
        Room room = room(56, true, true);
        AtomicInteger executions = new AtomicInteger();
        WiredEngine engine = engine(room, delayedEffect(executions, false));
        when(scheduler.run(any(Runnable.class), anyLong())).thenReturn(null);

        assertTrue(engine.handleEvent(event(room, null)));

        assertEquals(0, executions.get());
        assertEquals(0, engine.getDiagnosticsSnapshot(room.getId()).getDelayedEventsPending());
    }

    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).run(task.capture(), longThat(delay -> delay >= 0L && delay <= 500L));
        return task.getValue();
    }

    private static WiredEngine engine(Room room, IWiredEffect effect) {
        HabboItem triggerItem = mock(HabboItem.class);
        when(triggerItem.getId()).thenReturn(8001);
        IWiredTrigger trigger = new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem item, WiredEvent event) {
                return true;
            }
        };
        WiredStack stack = new WiredStack(triggerItem, trigger, List.of(), List.of(effect));
        return new WiredEngine(mock(WiredServices.class), (candidate, type) -> List.of(stack), 100, ignoredId -> room);
    }

    private static IWiredEffect delayedEffect(AtomicInteger executions, boolean requiresActor) {
        return new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {
                executions.incrementAndGet();
            }

            @Override
            public int getDelay() {
                return 1;
            }

            @Override
            public boolean requiresActor() {
                return requiresActor;
            }
        };
    }

    private static Room room(int id, boolean loaded, boolean occupied) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(loaded);
        when(room.getLifecycleGeneration()).thenReturn(1L);
        when(room.getHabbos()).thenReturn(occupied ? List.of(mock(Habbo.class)) : List.of());
        return room;
    }

    private static WiredEvent event(Room room, RoomUnit actor) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .actor(actor)
                .createdAtMs(System.currentTimeMillis())
                .build();
    }
}
