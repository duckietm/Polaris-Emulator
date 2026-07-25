package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredTrigger;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "polaris.profile.wired-dispatch", matches = "true")
class WiredDispatchPerformanceProfileTest {

    private static final int[] STACK_COUNTS = {0, 10, 100, 1000};
    private static final int[] SAMPLES = {100_000, 20_000, 5_000, 1_000};
    private static final int[] WARMUP_SAMPLES = {20_000, 2_000, 200, 20};
    private static volatile long blackhole;

    @Test
    void recordsComparableDispatchLatencyAllocationAndRetainedState() throws Exception {
        Path profileDirectory = Path.of("target", "profiles");
        Files.createDirectories(profileDirectory);
        Path recordingPath = profileDirectory.resolve("wired-dispatch-baseline.jfr");
        Path summaryPath = profileDirectory.resolve("wired-dispatch-baseline.txt");
        List<ScenarioSummary> summaries = new ArrayList<>();
        List<PreparedScenario> scenarios = new ArrayList<>();

        int previousEventLimit = WiredEngine.MAX_EVENTS_PER_WINDOW;
        int previousUsageLimit = WiredEngine.MONITOR_USAGE_LIMIT;
        try {
            WiredEngine.MAX_EVENTS_PER_WINDOW = Integer.MAX_VALUE;
            WiredEngine.MONITOR_USAGE_LIMIT = Integer.MAX_VALUE;
            for (int index = 0; index < STACK_COUNTS.length; index++) {
                scenarios.add(prepareScenario(STACK_COUNTS[index], WARMUP_SAMPLES[index]));
            }
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ZERO);
                recording.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ZERO);
                recording.start();
                for (int index = 0; index < STACK_COUNTS.length; index++) {
                    summaries.add(profileScenario(scenarios.get(index), SAMPLES[index]));
                }
                recording.stop();
                recording.dump(recordingPath);
            }
        } finally {
            WiredEngine.MAX_EVENTS_PER_WINDOW = previousEventLimit;
            WiredEngine.MONITOR_USAGE_LIMIT = previousUsageLimit;
        }

        AllocationSummary allocations = summarizeAllocations(recordingPath);
        String summary = format(summaries, allocations);
        Files.writeString(summaryPath, summary);
        System.out.println("WIRED_DISPATCH_PROFILE " + summary.replace(System.lineSeparator(), " | "));

        assertFalse(summaries.isEmpty());
        assertTrue(blackhole > 0L);
    }

    private static PreparedScenario prepareScenario(int stackCount, int warmupSamples) {
        Room room = room(10_000 + stackCount);
        List<WiredStack> stacks = stacks(stackCount);
        WiredEngine engine = new WiredEngine(mock(WiredServices.class), (ignored, type) -> stacks, 10_000);

        for (int warmup = 0; warmup < warmupSamples; warmup++) {
            engine.handleEvent(event(room));
        }
        engine.clearRoomRateLimiters(room.getId());
        engine.clearRoomDiagnostics(room.getId());
        return new PreparedScenario(stackCount, room, stacks, engine);
    }

    private static ScenarioSummary profileScenario(PreparedScenario scenario, int samples) throws Exception {
        long[] latencies = new long[samples];
        long startedAt = System.nanoTime();
        for (int sample = 0; sample < samples; sample++) {
            long started = System.nanoTime();
            if (scenario.engine().handleEvent(event(scenario.room()))) {
                blackhole++;
            }
            latencies[sample] = System.nanoTime() - started;
        }
        long elapsed = System.nanoTime() - startedAt;
        Arrays.sort(latencies);

        if (!scenario.stacks().isEmpty()) {
            int sourceItemId = scenario.stacks().getFirst().triggerItem().getId();
            scenario.engine().handleEventForSourceItem(event(scenario.room()), sourceItemId);
        }

        WiredRoomDiagnostics.Snapshot diagnostics =
                scenario.engine().getDiagnosticsSnapshot(scenario.room().getId());
        return new ScenarioSummary(
                scenario.stackCount(),
                samples,
                samples * 1_000_000_000.0 / elapsed,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                percentile(latencies, 0.99),
                diagnostics.getDelayedEventsPending(),
                scenario.engine().sourceStackCacheSize(),
                mapSize(scenario.engine(), "unseenIndices"));
    }

    private static List<WiredStack> stacks(int count) {
        List<WiredStack> stacks = new ArrayList<>(count);
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
        IWiredEffect effect = context -> blackhole++;
        for (int index = 0; index < count; index++) {
            HabboItem item = mock(HabboItem.class, withSettings().stubOnly());
            when(item.getId()).thenReturn(20_000 + index);
            stacks.add(new WiredStack(item, trigger, List.of(), List.of(effect)));
        }
        return List.copyOf(stacks);
    }

    private static Room room(int id) {
        Room room = mock(Room.class, withSettings().stubOnly());
        when(room.getId()).thenReturn(id);
        when(room.isLoaded()).thenReturn(true);
        return room;
    }

    private static WiredEvent event(Room room) {
        return WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .createdAtMs(System.currentTimeMillis())
                .build();
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static int mapSize(WiredEngine engine, String fieldName) throws Exception {
        Field field = WiredEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(engine);
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        throw new IllegalStateException(fieldName + " is not a map");
    }

    private static AllocationSummary summarizeAllocations(Path recordingPath) throws Exception {
        long events = 0L;
        long bytes = 0L;
        try (RecordingFile recording = new RecordingFile(recordingPath)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (event.getEventType().getName().startsWith("jdk.ObjectAllocation")) {
                    events++;
                    bytes += event.getLong("allocationSize");
                }
            }
        }
        return new AllocationSummary(events, bytes);
    }

    private static String format(List<ScenarioSummary> summaries, AllocationSummary allocations) {
        StringBuilder result = new StringBuilder();
        result.append("Polaris wired dispatch baseline (JDK ")
                .append(System.getProperty("java.version"))
                .append(")\n");
        result.append("Units: latency=nanoseconds, throughput=dispatches/second\n");
        for (ScenarioSummary summary : summaries) {
            result.append(String.format(
                    "stacks=%d samples=%d throughput=%.2f p50=%d p95=%d p99=%d delayedQueue=%d sourceCache=%d unseenCache=%d%n",
                    summary.stackCount(),
                    summary.samples(),
                    summary.throughput(),
                    summary.p50Nanos(),
                    summary.p95Nanos(),
                    summary.p99Nanos(),
                    summary.delayedQueueDepth(),
                    summary.sourceCacheEntries(),
                    summary.unseenCacheEntries()));
        }
        result.append("allocationEvents=").append(allocations.events()).append('\n');
        result.append("allocationBytes=").append(allocations.bytes()).append('\n');
        return result.toString();
    }

    private record ScenarioSummary(
            int stackCount,
            int samples,
            double throughput,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            int delayedQueueDepth,
            int sourceCacheEntries,
            int unseenCacheEntries) {}

    private record PreparedScenario(int stackCount, Room room, List<WiredStack> stacks, WiredEngine engine) {}

    private record AllocationSummary(long events, long bytes) {}
}
