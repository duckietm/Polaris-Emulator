package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "polaris.profile.wired-dispatcher", matches = "true")
class WiredEventDispatcherPerformanceProfileTest {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 9;
    private static volatile long blackhole;

    @Test
    void comparesExtractedDispatcherWithPreExtractionLoopInOneJvm() {
        int previousEventLimit = WiredEngine.MAX_EVENTS_PER_WINDOW;
        try {
            WiredEngine.MAX_EVENTS_PER_WINDOW = Integer.MAX_VALUE;
            compare(0, 20_000);
            compare(10, 2_000);
            assertTrue(blackhole > 0L);
        } finally {
            WiredEngine.MAX_EVENTS_PER_WINDOW = previousEventLimit;
        }
    }

    private static void compare(int stackCount, int operations) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(92_000 + stackCount);
        when(room.isLoaded()).thenReturn(true);
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(500L)
                .build();
        List<WiredStack> stacks = stacks(stackCount);
        WiredEventDispatcher.StackProcessor processor = (stack, ignoredEvent, time, negate) -> {
            blackhole++;
            return true;
        };
        WiredExecutionGuard extractedGuard = guard();
        WiredExecutionGuard legacyGuard = guard();
        WiredStackRepository extractedRepository = new WiredStackRepository((candidate, type) -> stacks);
        WiredStackRepository legacyRepository = new WiredStackRepository((candidate, type) -> stacks);
        WiredEventDispatcher extracted = new WiredEventDispatcher(
                extractedGuard, extractedRepository, processor, (candidate, format, args) -> {});
        LegacyDispatcher legacy = new LegacyDispatcher(legacyGuard, legacyRepository, processor);

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            runExtracted(extracted, event, operations);
            runLegacy(legacy, event, operations);
            clearInvocations(room);
        }

        List<Double> ratios = new ArrayList<>();
        List<Double> extractedNanos = new ArrayList<>();
        List<Double> legacyNanos = new ArrayList<>();
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long extractedElapsed;
            long legacyElapsed;
            if ((round & 1) == 0) {
                extractedElapsed = runExtracted(extracted, event, operations);
                legacyElapsed = runLegacy(legacy, event, operations);
            } else {
                legacyElapsed = runLegacy(legacy, event, operations);
                extractedElapsed = runExtracted(extracted, event, operations);
            }

            double extractedPerOperation = extractedElapsed / (double) operations;
            double legacyPerOperation = legacyElapsed / (double) operations;
            extractedNanos.add(extractedPerOperation);
            legacyNanos.add(legacyPerOperation);
            ratios.add(extractedPerOperation / legacyPerOperation);
            clearInvocations(room);
        }

        double extractedMedian = median(extractedNanos);
        double legacyMedian = median(legacyNanos);
        double ratioMedian = median(ratios);
        System.out.printf(
                "WIRED_DISPATCHER_AB_PROFILE stacks=%d operations=%d rounds=%d legacyMedianNs=%.2f extractedMedianNs=%.2f medianRatio=%.4f%n",
                stackCount, operations, MEASURED_ROUNDS, legacyMedian, extractedMedian, ratioMedian);
        assertTrue(
                ratioMedian <= 1.15D,
                () -> String.format(
                        "Extracted dispatcher ratio %.4f exceeded the 1.15 gate for %d stacks",
                        ratioMedian, stackCount));
    }

    private static WiredExecutionGuard guard() {
        return new WiredExecutionGuard(
                System::currentTimeMillis,
                (room, eventType, count, limits, banned) -> {},
                (room, eventType, kind, depth, maximum) -> {});
    }

    private static long runExtracted(WiredEventDispatcher dispatcher, WiredEvent event, int operations) {
        long started = System.nanoTime();
        for (int operation = 0; operation < operations; operation++) {
            if (dispatcher.dispatch(event, false)) {
                blackhole++;
            }
        }
        return System.nanoTime() - started;
    }

    private static long runLegacy(LegacyDispatcher dispatcher, WiredEvent event, int operations) {
        long started = System.nanoTime();
        for (int operation = 0; operation < operations; operation++) {
            if (dispatcher.dispatch(event, false)) {
                blackhole++;
            }
        }
        return System.nanoTime() - started;
    }

    private static List<WiredStack> stacks(int count) {
        List<WiredStack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            HabboItem item = mock(HabboItem.class);
            when(item.getId()).thenReturn(93_000 + index);
            stacks.add(new WiredStack(item, mock(IWiredTrigger.class), List.of(), List.of()));
        }
        return List.copyOf(stacks);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** Test-local copy of the pre-extraction normal event admission and stack loop. */
    private static final class LegacyDispatcher {
        private final WiredExecutionGuard guard;
        private final WiredStackRepository repository;
        private final WiredEventDispatcher.StackProcessor processor;

        private LegacyDispatcher(
                WiredExecutionGuard guard,
                WiredStackRepository repository,
                WiredEventDispatcher.StackProcessor processor) {
            this.guard = guard;
            this.repository = repository;
            this.processor = processor;
        }

        private boolean dispatch(WiredEvent event, boolean negateConditions) {
            if (event == null) {
                return false;
            }
            Room room = event.getRoom();
            if (room == null || !room.isLoaded()) {
                return false;
            }
            if (!this.guard.tryEnter(room, event.getType(), WiredExecutionGuard.EntryKind.EVENT)) {
                return false;
            }

            try {
                List<WiredStack> stacks = this.repository.getStacks(room, event.getType());
                if (stacks.isEmpty()) {
                    return false;
                }

                boolean anyTriggered = false;
                for (WiredStack stack : stacks) {
                    try {
                        if (this.processor.process(stack, event, event.getCreatedAtMs(), negateConditions)) {
                            anyTriggered = true;
                        }
                    } catch (WiredLimitException ignored) {
                        // Exact failure-isolation shape; this fixture does not throw.
                    } catch (Exception ignored) {
                        // Exact failure-isolation shape; this fixture does not throw.
                    }
                }
                return anyTriggered;
            } finally {
                this.guard.exit(room.getId());
            }
        }
    }
}
