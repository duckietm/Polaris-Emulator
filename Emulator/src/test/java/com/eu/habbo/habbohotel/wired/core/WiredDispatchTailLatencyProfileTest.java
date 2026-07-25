package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Paired, same-JVM p99 comparison against the pre-extraction empty-dispatch path. */
@EnabledIfSystemProperty(named = "polaris.profile.wired-tail", matches = "true")
class WiredDispatchTailLatencyProfileTest {

    private static final int OPERATIONS = 50_000;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 11;
    private static volatile long blackhole;

    @Test
    void extractedEmptyDispatchHasNoWorseMedianPairedP99ThanLegacyPath() {
        Room room = mock(Room.class, withSettings().stubOnly());
        when(room.getId()).thenReturn(94_001);
        when(room.isLoaded()).thenReturn(true);
        WiredStackIndex index = (ignoredRoom, ignoredType) -> List.of();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                System::currentTimeMillis,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});
        WiredEventDispatcher extracted = new WiredEventDispatcher(
                guard,
                new WiredStackRepository(index),
                (stack, event, time, negate) -> false,
                (ignoredRoom, format, arguments) -> {});
        LegacyEmptyDispatcher legacy = new LegacyEmptyDispatcher(index);

        int previousEventLimit = WiredEngine.MAX_EVENTS_PER_WINDOW;
        try {
            WiredEngine.MAX_EVENTS_PER_WINDOW = Integer.MAX_VALUE;
            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                profileExtracted(extracted, room);
                profileLegacy(legacy, room);
            }

            List<Long> extractedP99 = new ArrayList<>();
            List<Long> legacyP99 = new ArrayList<>();
            List<Double> pairedRatios = new ArrayList<>();
            for (int round = 0; round < MEASURED_ROUNDS; round++) {
                long extractedRoundP99;
                long baseline;
                if ((round & 1) == 0) {
                    extractedRoundP99 = profileExtracted(extracted, room);
                    baseline = profileLegacy(legacy, room);
                } else {
                    baseline = profileLegacy(legacy, room);
                    extractedRoundP99 = profileExtracted(extracted, room);
                }
                extractedP99.add(extractedRoundP99);
                legacyP99.add(baseline);
                pairedRatios.add(extractedRoundP99 / (double) baseline);
            }

            long extractedMedianP99 = medianLong(extractedP99);
            long legacyMedianP99 = medianLong(legacyP99);
            double medianPairedRatio = medianDouble(pairedRatios);
            System.out.printf(
                    "WIRED_TAIL_AB_PROFILE operations=%d rounds=%d legacyMedianP99Ns=%d extractedMedianP99Ns=%d medianPairedRatio=%.4f%n",
                    OPERATIONS, MEASURED_ROUNDS, legacyMedianP99, extractedMedianP99, medianPairedRatio);

            assertTrue(blackhole > 0L);
            assertTrue(
                    medianPairedRatio <= 1.0D,
                    () -> String.format(
                            "Extracted zero-candidate median paired p99 ratio %.4f was worse than legacy",
                            medianPairedRatio));
            assertTrue(
                    extractedMedianP99 <= legacyMedianP99,
                    () -> String.format(
                            "Extracted zero-candidate median p99 %dns exceeded legacy %dns",
                            extractedMedianP99, legacyMedianP99));
        } finally {
            WiredEngine.MAX_EVENTS_PER_WINDOW = previousEventLimit;
        }
    }

    private static long profileExtracted(WiredEventDispatcher dispatcher, Room room) {
        long[] latencies = new long[OPERATIONS];
        for (int operation = 0; operation < OPERATIONS; operation++) {
            long started = System.nanoTime();
            if (dispatcher.dispatch(event(room), false)) {
                blackhole++;
            }
            latencies[operation] = System.nanoTime() - started;
        }
        return p99(latencies);
    }

    private static long profileLegacy(LegacyEmptyDispatcher dispatcher, Room room) {
        long[] latencies = new long[OPERATIONS];
        for (int operation = 0; operation < OPERATIONS; operation++) {
            long started = System.nanoTime();
            if (dispatcher.dispatch(event(room))) {
                blackhole++;
            }
            latencies[operation] = System.nanoTime() - started;
        }
        return p99(latencies);
    }

    private static WiredEvent event(Room room) {
        blackhole++;
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(System.currentTimeMillis())
                .build();
    }

    private static long p99(long[] values) {
        Arrays.sort(values);
        return values[(int) Math.ceil(values.length * 0.99D) - 1];
    }

    private static long medianLong(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static double medianDouble(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** Exact empty-stack control-flow shape from revision 752d242f before collaborator extraction. */
    private static final class LegacyEmptyDispatcher {
        private final WiredStackIndex index;
        private final ConcurrentHashMap<Integer, Integer> recursionDepth = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LegacyRateTracker> rateLimiters = new ConcurrentHashMap<>();

        private LegacyEmptyDispatcher(WiredStackIndex index) {
            this.index = index;
        }

        private boolean dispatch(WiredEvent event) {
            if (event == null) {
                return false;
            }
            Room room = event.getRoom();
            if (room == null || !room.isLoaded()) {
                return false;
            }

            int roomId = room.getId();
            if (isRateLimited(roomId, event.getType())) {
                return false;
            }

            int currentDepth = this.recursionDepth.merge(roomId, 1, Integer::sum);
            if (currentDepth > WiredEngine.MAX_RECURSION_DEPTH) {
                this.recursionDepth.merge(roomId, -1, Integer::sum);
                return false;
            }

            try {
                List<WiredStack> stacks = this.index.getStacks(room, event.getType());
                return !stacks.isEmpty();
            } finally {
                this.recursionDepth.compute(
                        roomId, (ignored, depth) -> (depth == null || depth <= 1) ? null : depth - 1);
            }
        }

        private boolean isRateLimited(int roomId, WiredEvent.Type eventType) {
            String key = roomId + ":" + eventType.name();
            long now = System.currentTimeMillis();
            LegacyRateTracker tracker = this.rateLimiters.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new LegacyRateTracker(now);
                }
                existing.record(now);
                return existing;
            });
            return tracker.isLimited();
        }
    }

    private static final class LegacyRateTracker {
        private long windowStart;
        private int eventCount;

        private LegacyRateTracker(long now) {
            this.windowStart = now;
            this.eventCount = 1;
        }

        private synchronized void record(long now) {
            if (now - this.windowStart > WiredEngine.RATE_LIMIT_WINDOW_MS) {
                this.windowStart = now;
                this.eventCount = 1;
            } else {
                this.eventCount++;
            }
        }

        private synchronized boolean isLimited() {
            return this.eventCount > WiredEngine.MAX_EVENTS_PER_WINDOW;
        }
    }
}
