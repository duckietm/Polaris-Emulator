package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "polaris.profile.wired-guard", matches = "true")
class WiredExecutionGuardPerformanceProfileTest {

    private static final int OPERATIONS = 20_000;
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 9;
    private static volatile long blackhole;

    @Test
    void comparesExtractedGuardWithPreExtractionAdmissionInOneJvm() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(91_001);
        LegacyAdmission legacy = new LegacyAdmission();
        WiredExecutionGuard extracted = new WiredExecutionGuard(
                System::currentTimeMillis,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});

        int previousEventLimit = WiredEngine.MAX_EVENTS_PER_WINDOW;
        int previousRecursionLimit = WiredEngine.MAX_RECURSION_DEPTH;
        long previousBanDuration = WiredEngine.WIRED_BAN_DURATION_MS;
        try {
            WiredEngine.MAX_EVENTS_PER_WINDOW = Integer.MAX_VALUE;
            WiredEngine.MAX_RECURSION_DEPTH = 10;
            WiredEngine.WIRED_BAN_DURATION_MS = 0L;

            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                runLegacy(legacy, room);
                runExtracted(extracted, room);
                clearInvocations(room);
            }

            List<Double> legacyNanos = new ArrayList<>();
            List<Double> extractedNanos = new ArrayList<>();
            List<Double> ratios = new ArrayList<>();
            for (int round = 0; round < MEASURED_ROUNDS; round++) {
                long legacyElapsed;
                long extractedElapsed;
                if ((round & 1) == 0) {
                    extractedElapsed = runExtracted(extracted, room);
                    legacyElapsed = runLegacy(legacy, room);
                } else {
                    legacyElapsed = runLegacy(legacy, room);
                    extractedElapsed = runExtracted(extracted, room);
                }

                double legacyPerOperation = legacyElapsed / (double) OPERATIONS;
                double extractedPerOperation = extractedElapsed / (double) OPERATIONS;
                legacyNanos.add(legacyPerOperation);
                extractedNanos.add(extractedPerOperation);
                ratios.add(extractedPerOperation / legacyPerOperation);
                clearInvocations(room);
            }

            double legacyMedian = median(legacyNanos);
            double extractedMedian = median(extractedNanos);
            double ratioMedian = median(ratios);
            System.out.printf(
                    "WIRED_GUARD_AB_PROFILE operations=%d rounds=%d legacyMedianNs=%.2f extractedMedianNs=%.2f medianRatio=%.4f%n",
                    OPERATIONS, MEASURED_ROUNDS, legacyMedian, extractedMedian, ratioMedian);

            assertTrue(blackhole > 0L);
            assertTrue(
                    ratioMedian <= 1.15D,
                    () -> String.format("Extracted guard median ratio %.4f exceeded the 1.15 gate", ratioMedian));
        } finally {
            WiredEngine.MAX_EVENTS_PER_WINDOW = previousEventLimit;
            WiredEngine.MAX_RECURSION_DEPTH = previousRecursionLimit;
            WiredEngine.WIRED_BAN_DURATION_MS = previousBanDuration;
        }
    }

    private static long runExtracted(WiredExecutionGuard guard, Room room) {
        long started = System.nanoTime();
        for (int operation = 0; operation < OPERATIONS; operation++) {
            if (guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT)) {
                blackhole++;
                guard.exit(room.getId());
            }
        }
        return System.nanoTime() - started;
    }

    private static long runLegacy(LegacyAdmission guard, Room room) {
        long started = System.nanoTime();
        for (int operation = 0; operation < OPERATIONS; operation++) {
            if (guard.tryEnter(room, WiredEvent.Type.CUSTOM)) {
                blackhole++;
                guard.exit(room.getId());
            }
        }
        return System.nanoTime() - started;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** Test-local copy of the pre-extraction recursion/rate admission hot path. */
    private static final class LegacyAdmission {
        private final ConcurrentHashMap<Integer, Integer> recursionDepth = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LegacyRateTracker> rateLimiters = new ConcurrentHashMap<>();

        private boolean tryEnter(Room room, WiredEvent.Type eventType) {
            int roomId = room.getId();
            if (isRateLimited(roomId, eventType)) {
                return false;
            }

            int currentDepth = this.recursionDepth.merge(roomId, 1, Integer::sum);
            if (currentDepth <= WiredEngine.MAX_RECURSION_DEPTH) {
                return true;
            }
            this.recursionDepth.merge(roomId, -1, Integer::sum);
            return false;
        }

        private void exit(int roomId) {
            this.recursionDepth.compute(roomId, (ignored, depth) -> (depth == null || depth <= 1) ? null : depth - 1);
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
