package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredEventDispatcherTest {

    @Test
    void rejectsInvalidEntryWithoutLookupOrRetainedRecursion() {
        Room room = room(81, false);
        List<String> calls = new ArrayList<>();
        WiredExecutionGuard guard = guard();
        WiredEventDispatcher dispatcher = dispatcher(
                guard,
                (candidate, type) -> {
                    calls.add("lookup");
                    return List.of();
                },
                (stack, event, time, negate) -> true,
                calls);

        assertFalse(dispatcher.dispatch(null, false));
        assertFalse(dispatcher.dispatch(event(room, WiredEvent.Type.CUSTOM), false));
        assertFalse(dispatcher.dispatchForSourceItem(event(room, WiredEvent.Type.CUSTOM), 0));
        assertEquals(List.of(), calls);
        assertEquals(0, guard.recursionDepth(room.getId()));
    }

    @Test
    void isolatesStackFailuresAndAlwaysReleasesGuardAdmission() {
        Room room = room(82, true);
        WiredStack limited = stack(item(8_201), mock(IWiredTrigger.class));
        WiredStack failing = stack(item(8_202), mock(IWiredTrigger.class));
        WiredStack succeeding = stack(item(8_203), mock(IWiredTrigger.class));
        List<String> calls = new ArrayList<>();
        WiredExecutionGuard guard = guard();
        WiredEventDispatcher dispatcher = dispatcher(
                guard,
                (candidate, type) -> List.of(limited, failing, succeeding),
                (stack, event, time, negate) -> {
                    calls.add("stack-" + stack.triggerItem().getId());
                    if (stack == limited) {
                        throw new WiredLimitException("fixture-limit");
                    }
                    if (stack == failing) {
                        throw new IllegalStateException("fixture-error");
                    }
                    return true;
                },
                calls);

        assertTrue(dispatcher.dispatch(event(room, WiredEvent.Type.CUSTOM), true));
        assertEquals(0, guard.recursionDepth(room.getId()));
        assertTrue(calls.containsAll(List.of("stack-8201", "stack-8202", "stack-8203")));
        assertTrue(calls.contains("Stack execution stopped (limit): {}"));
        assertTrue(calls.contains("Stack error: {}"));
    }

    @Test
    void preservesUserSaysSuppressionAndSourceItemFiltering() {
        Room room = room(83, true);
        WiredTriggerHabboSaysKeyword visible = mock(WiredTriggerHabboSaysKeyword.class);
        when(visible.getId()).thenReturn(8_301);
        when(visible.isHideMessage()).thenReturn(false);
        WiredTriggerHabboSaysKeyword hidden = mock(WiredTriggerHabboSaysKeyword.class);
        when(hidden.getId()).thenReturn(8_302);
        when(hidden.isHideMessage()).thenReturn(true);
        WiredStack visibleStack = stack(visible, mock(IWiredTrigger.class));
        WiredStack hiddenStack = stack(hidden, mock(IWiredTrigger.class));
        AtomicReference<List<WiredStack>> indexed = new AtomicReference<>(List.of(visibleStack));
        List<String> calls = new ArrayList<>();
        WiredEventDispatcher dispatcher = dispatcher(
                guard(),
                (candidate, type) -> indexed.get(),
                (stack, event, time, negate) -> {
                    calls.add(stack.triggerItem().getId() + ":" + negate);
                    return true;
                },
                calls);

        WiredEvent says = event(room, WiredEvent.Type.USER_SAYS);
        assertFalse(dispatcher.dispatch(says, false));
        indexed.set(List.of(visibleStack, hiddenStack));
        assertTrue(dispatcher.dispatch(says, false));
        assertTrue(dispatcher.dispatchForSourceItem(says, hidden.getId()));
        assertEquals(
                List.of("8301:false", "8301:false", "8302:false", "8302:false"),
                calls.stream().filter(value -> value.matches("\\d+:.*")).toList());
    }

    private static WiredEventDispatcher dispatcher(
            WiredExecutionGuard guard,
            WiredStackIndex index,
            WiredEventDispatcher.StackProcessor processor,
            List<String> diagnostics) {
        return new WiredEventDispatcher(
                guard,
                new WiredStackRepository(index),
                processor,
                (room, format, arguments) -> diagnostics.add(format));
    }

    private static WiredExecutionGuard guard() {
        return new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(10, 100, 1_000L, 0L, 1_000, 100, 10, 50, 150, 70, 5, 2, 60),
                () -> 1_000L,
                (room, eventType, count, limits, banned) -> {},
                (room, eventType, kind, depth, maximum) -> {});
    }

    private static Room room(int id, boolean loaded) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(loaded);
        return room;
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WiredStack stack(HabboItem item, IWiredTrigger trigger) {
        return new WiredStack(item, trigger, List.of(), List.of());
    }

    private static WiredEvent event(Room room, WiredEvent.Type type) {
        return WiredEvent.builder(type, room).createdAtMs(500L).build();
    }
}
