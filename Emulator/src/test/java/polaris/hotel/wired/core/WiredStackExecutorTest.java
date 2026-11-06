package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.api.IWiredCondition;
import polaris.hotel.wired.api.IWiredEffect;
import polaris.hotel.wired.api.IWiredTrigger;
import polaris.hotel.wired.api.WiredStack;

class WiredStackExecutorTest {

    @Test
    void eventExecutionPreservesTriggerSelectorConditionAndEffectOrder() {
        List<String> calls = new ArrayList<>();
        Room room = room(91);
        IWiredTrigger trigger = trigger(calls, true);
        IWiredCondition condition = context -> {
            calls.add("condition");
            return true;
        };
        IWiredEffect effect = context -> calls.add("effect");
        WiredStack stack = new WiredStack(item(9_101), trigger, List.of(condition), List.of(effect));
        WiredStackExecutor executor = executor(calls, 1_500L);

        assertTrue(executor.executeEvent(stack, event(room), 1_000L, false));
        assertEquals(
                List.of(
                        "trigger",
                        "selectors",
                        "filters",
                        "targets",
                        "arrayCapture",
                        "condition",
                        "finalize:1000",
                        "effect"),
                calls);
    }

    @Test
    void directExecutionSkipsTriggerMatchingAndUsesInjectedClock() {
        List<String> calls = new ArrayList<>();
        Room room = room(92);
        IWiredTrigger trigger = trigger(calls, false);
        WiredStack stack = new WiredStack(item(9_201), trigger, List.of(), List.of(context -> calls.add("effect")));
        WiredStackExecutor executor = executor(calls, 2_345L);

        assertTrue(executor.executeDirect(stack, event(room), false));
        assertEquals(List.of("selectors", "filters", "targets", "arrayCapture", "finalize:2345", "effect"), calls);
    }

    @Test
    void invalidActorRequirementStopsBeforeHookSideEffectsAndRecovers() {
        List<String> calls = new ArrayList<>();
        Room room = room(93);
        AtomicInteger matches = new AtomicInteger();
        IWiredTrigger trigger = new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(UserItem triggerItem, WiredEvent event) {
                matches.incrementAndGet();
                return true;
            }

            @Override
            public boolean requiresActor() {
                return true;
            }
        };
        WiredStack stack = new WiredStack(item(9_301), trigger, List.of(), List.of(context -> calls.add("effect")));
        WiredStackExecutor executor = executor(calls, 3_000L);

        assertFalse(executor.executeEvent(stack, event(room), 3_000L, false));
        assertFalse(executor.executeDirect(stack, null, false));
        assertEquals(1, matches.get());
        assertEquals(List.of(), calls);
    }

    @Test
    void aSelfFeedingChainStopsAtItsStackBudgetAndTheNextChainStartsFresh() {
        List<String> calls = new ArrayList<>();
        Room room = room(94);
        AtomicInteger runs = new AtomicInteger();
        WiredStackExecutor[] executor = new WiredStackExecutor[1];
        WiredStack[] stack = new WiredStack[1];
        // Like a signal sent to its own antenna: every run starts the stack again.
        IWiredEffect loop = context -> {
            runs.incrementAndGet();
            executor[0].executeDirect(stack[0], event(room), false);
        };
        stack[0] = new WiredStack(item(9_401), trigger(calls, false), List.of(), List.of(loop));
        executor[0] = executor(calls, 4_000L);
        executor[0].setMaxStacksPerChain(5);

        assertTrue(executor[0].executeDirect(stack[0], event(room), false));
        assertEquals(5, runs.get());

        runs.set(0);
        assertTrue(executor[0].executeDirect(stack[0], event(room), false));
        assertEquals(5, runs.get(), "a new chain gets a new budget");
    }

    private static WiredStackExecutor executor(List<String> calls, long now) {
        WiredConditionEvaluator conditionEvaluator = new WiredConditionEvaluator((room, format, arguments) -> {});
        WiredExecutionGuard executionGuard = new WiredExecutionGuard(
                new WiredExecutionGuard.Limits(10, 100, 1_000L, 0L, 10_000, 10_000, 100, 50, 150, 70, 5, 2, 60),
                () -> now,
                (room, eventType, count, limits, banned) -> {},
                (room, eventType, kind, depth, maximum) -> {});
        WiredStackExecutor.Hooks hooks = new WiredStackExecutor.Hooks() {
            @Override
            public List<InteractionWiredEffect> executeSelectors(WiredStack stack, WiredContext context) {
                calls.add("selectors");
                return List.of();
            }

            @Override
            public void applySelectionFilterExtras(
                    WiredStack stack, WiredContext context, List<InteractionWiredEffect> executedSelectors) {
                calls.add("filters");
            }

            @Override
            public boolean selectorsHaveRequiredTargets(
                    List<InteractionWiredEffect> executedSelectors, WiredContext context) {
                calls.add("targets");
                return true;
            }

            @Override
            public void captureArrayEntries(Room room, WiredStack stack, WiredContext context) {
                calls.add("arrayCapture");
            }

            @Override
            public void finalizeSelectors(
                    List<InteractionWiredEffect> executedSelectors, WiredContext context, long currentTime) {
                calls.add("finalize:" + currentTime);
            }

            @Override
            public void executeEffects(
                    WiredStack stack, List<IWiredEffect> effects, WiredContext context, long currentTime) {
                for (IWiredEffect effect : effects) {
                    effect.execute(context);
                }
            }
        };
        return new WiredStackExecutor(
                mock(WiredServices.class),
                100,
                conditionEvaluator,
                new WiredEffectPlanner(),
                executionGuard,
                hooks,
                () -> now,
                (room, format, arguments) -> {},
                new WiredStructuredDiagnostics(() -> false, (format, arguments) -> {}));
    }

    private static IWiredTrigger trigger(List<String> calls, boolean matches) {
        return new IWiredTrigger() {
            @Override
            public WiredEvent.Type listensTo() {
                return WiredEvent.Type.CUSTOM;
            }

            @Override
            public boolean matches(UserItem triggerItem, WiredEvent event) {
                calls.add("trigger");
                return matches;
            }
        };
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static UserItem item(int id) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static WiredEvent event(Room room) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(1_000L)
                .build();
    }
}
