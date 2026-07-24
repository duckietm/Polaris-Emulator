package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredExecutionSemanticsCompatibilityTest {

    @Test
    void triggerSelectorConditionAndEffectsKeepObservableOrder() {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        Room room = room(41);
        HabboItem triggerItem = triggerItem(7001);
        IWiredTrigger trigger = trigger(calls, true);
        IWiredEffect selector = selector(calls);
        IWiredCondition condition = context -> {
            calls.add("condition");
            return true;
        };
        IWiredEffect first = effect(calls, "effect-1");
        IWiredEffect second = effect(calls, "effect-2");
        WiredStack stack = new WiredStack(triggerItem, trigger, List.of(condition), List.of(selector, first, second));
        WiredEngine engine = engine(room, stack);

        assertTrue(engine.handleEvent(event(room)));
        assertEquals(List.of("trigger", "selector", "condition", "effect-1", "effect-2"), calls);
    }

    @Test
    void actorRequirementStopsBeforeConditionsAndEffects() {
        List<String> calls = new ArrayList<>();
        Room room = room(42);
        IWiredTrigger trigger = new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem triggerItem, WiredEvent event) {
                calls.add("trigger");
                return true;
            }

            @Override
            public boolean requiresActor() {
                return true;
            }
        };
        WiredStack stack = new WiredStack(triggerItem(7002), trigger, List.of(), List.of(effect(calls, "effect")));

        assertFalse(engine(room, stack).handleEvent(event(room)));
        assertEquals(List.of("trigger"), calls);
    }

    @Test
    void thresholdConditionModesRetainTheirCurrentMeaning() {
        Room room = room(43);
        AtomicInteger executions = new AtomicInteger();
        List<IWiredCondition> conditions = List.of(context -> true, context -> false, context -> true);
        IWiredEffect effect = context -> executions.incrementAndGet();

        WiredStack exactlyTwo = new WiredStack(
                triggerItem(7003),
                trigger(new ArrayList<>(), true),
                conditions,
                List.of(effect),
                WiredExtraOrEval.MODE_EXACTLY,
                2,
                false,
                false,
                false);
        WiredStack all = new WiredStack(
                triggerItem(7004),
                trigger(new ArrayList<>(), true),
                conditions,
                List.of(effect),
                WiredExtraOrEval.MODE_ALL,
                1,
                false,
                false,
                false);

        assertTrue(engine(room, exactlyTwo).handleEvent(event(room)));
        assertFalse(engine(room, all).handleEvent(event(room)));
        assertEquals(1, executions.get());
    }

    @Test
    void unseenFallbackRemainsRoundRobinPerTriggerItem() {
        List<String> calls = new ArrayList<>();
        Room room = room(44);
        WiredStack stack = new WiredStack(
                triggerItem(7005),
                trigger(new ArrayList<>(), true),
                List.of(),
                List.of(effect(calls, "first"), effect(calls, "second"), effect(calls, "third")),
                WiredExtraOrEval.MODE_ALL,
                1,
                false,
                true,
                false);
        WiredEngine engine = engine(room, stack);

        assertTrue(engine.handleEvent(event(room)));
        assertTrue(engine.handleEvent(event(room)));
        assertTrue(engine.handleEvent(event(room)));
        assertTrue(engine.handleEvent(event(room)));
        assertEquals(List.of("first", "second", "third", "first"), calls);
    }

    @Test
    void effectFailureIsIsolatedFromFollowingImmediateEffects() {
        List<String> calls = new ArrayList<>();
        Room room = room(45);
        IWiredEffect failing = context -> {
            calls.add("failing");
            throw new IllegalStateException("fixture");
        };
        WiredStack stack = new WiredStack(
                triggerItem(7006),
                trigger(new ArrayList<>(), true),
                List.of(),
                List.of(failing, effect(calls, "following")));

        assertTrue(engine(room, stack).handleEvent(event(room)));
        assertEquals(List.of("failing", "following"), calls);
    }

    @Test
    void sourceItemCacheChangesOnlyAfterExplicitInvalidation() {
        List<String> calls = new ArrayList<>();
        Room room = room(46);
        HabboItem source = triggerItem(7007);
        WiredStack original =
                new WiredStack(source, trigger(new ArrayList<>(), true), List.of(), List.of(effect(calls, "original")));
        WiredStack replacement = new WiredStack(
                source, trigger(new ArrayList<>(), true), List.of(), List.of(effect(calls, "replacement")));
        AtomicReference<List<WiredStack>> indexed = new AtomicReference<>(List.of(original));
        WiredEngine engine = new WiredEngine(mock(WiredServices.class), (ignored, type) -> indexed.get(), 100);

        assertTrue(engine.handleEventForSourceItem(event(room), source.getId()));
        indexed.set(List.of(replacement));
        assertTrue(engine.handleEventForSourceItem(event(room), source.getId()));
        engine.clearRoomSourceStackCache(room.getId());
        assertTrue(engine.handleEventForSourceItem(event(room), source.getId()));

        assertEquals(List.of("original", "original", "replacement"), calls);
    }

    @Test
    void concurrentDispatchAgainstOneStackCompletesWithoutLostEffects() throws Exception {
        int workers = 8;
        Room room = room(47);
        AtomicInteger executions = new AtomicInteger();
        WiredStack stack = new WiredStack(
                triggerItem(7008),
                trigger(new ArrayList<>(), true),
                List.of(),
                List.of(context -> executions.incrementAndGet()));
        WiredEngine engine = engine(room, stack);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                results.add(executor.submit(() -> {
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    return engine.handleEvent(event(room));
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(2, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(workers, executions.get());
    }

    private static WiredEngine engine(Room room, WiredStack stack) {
        return new WiredEngine(
                mock(WiredServices.class), (candidate, type) -> candidate == room ? List.of(stack) : List.of(), 100);
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static HabboItem triggerItem(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WiredEvent event(Room room) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(System.currentTimeMillis())
                .build();
    }

    private static IWiredTrigger trigger(List<String> calls, boolean matches) {
        return new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(HabboItem triggerItem, WiredEvent event) {
                calls.add("trigger");
                return matches;
            }
        };
    }

    private static IWiredEffect selector(List<String> calls) {
        return new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {
                calls.add("selector");
            }

            @Override
            public boolean isSelector() {
                return true;
            }
        };
    }

    private static IWiredEffect effect(List<String> calls, String name) {
        return context -> calls.add(name);
    }
}
