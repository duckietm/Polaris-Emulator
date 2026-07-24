package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class WiredEffectCooldownConcurrencyTest {

    @Test
    void concurrentExecutionsAtomicallyAdmitOneEffectInsideItsCooldownWindow() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        Room room = room(101);
        InteractionWiredEffect effect = effect(room, executions);
        WiredEngine engine = engine(room, effect);
        WiredEvent event = event(room, 10_000L);
        int workers = 16;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        int previousMaxRecursionDepth = WiredEngine.MAX_RECURSION_DEPTH;

        try {
            WiredEngine.MAX_RECURSION_DEPTH = 100;
            for (int index = 0; index < workers; index++) {
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        engine.handleEvent(event);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            done.await(5, TimeUnit.SECONDS);

            assertEquals(1, executions.get());
        } finally {
            WiredEngine.MAX_RECURSION_DEPTH = previousMaxRecursionDepth;
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void exactCooldownBoundaryPreservesTheExistingFiftyMillisecondWindow() {
        AtomicInteger executions = new AtomicInteger();
        Room room = room(102);
        InteractionWiredEffect effect = effect(room, executions);
        WiredEngine engine = engine(room, effect);

        engine.handleEvent(event(room, 20_000L));
        engine.handleEvent(event(room, 20_049L));
        engine.handleEvent(event(room, 20_050L));

        assertEquals(2, executions.get());
    }

    private static WiredEngine engine(Room room, InteractionWiredEffect effect) {
        HabboItem triggerItem = mock(HabboItem.class);
        when(triggerItem.getId()).thenReturn(10_001);
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
        return new WiredEngine(mock(WiredServices.class), (candidate, type) -> List.of(stack), 100);
    }

    private static InteractionWiredEffect effect(Room room, AtomicInteger executions) {
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class, Answers.CALLS_REAL_METHODS);
        doAnswer(ignored -> {
                    executions.incrementAndGet();
                    return null;
                })
                .when(effect)
                .execute(any(WiredContext.class));
        doNothing().when(effect).activateBox(any(Room.class), nullable(RoomUnit.class), anyLong());
        return effect;
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static WiredEvent event(Room room, long createdAtMs) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(createdAtMs)
                .build();
    }
}
