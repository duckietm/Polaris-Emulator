package com.eu.habbo.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerObservabilityTest {

    @Test
    void snapshotTracksSchedulingAndSuccessfulExecution() {
        Scheduler scheduler = new Scheduler(10);

        invoke(scheduler, "markScheduled", new Class<?>[]{long.class}, 1_000L);
        invoke(scheduler, "markStarted", new Class<?>[]{long.class}, 1_000L);
        invoke(scheduler, "markCompleted", new Class<?>[]{long.class}, 1_025L);

        Object snapshot = snapshot(scheduler);
        assertEquals("Scheduler", field(snapshot, "name"));
        assertEquals(true, field(snapshot, "enabled"));
        assertEquals(10, field(snapshot, "intervalSeconds"));
        assertEquals(1_000L, field(snapshot, "lastStartedEpochMs"));
        assertEquals(1_025L, field(snapshot, "lastCompletedEpochMs"));
        assertEquals(1L, field(snapshot, "completedRuns"));
        assertEquals(0L, field(snapshot, "failedRuns"));
    }

    @Test
    void snapshotTracksFailuresWithoutCountingThemAsCompletions() {
        Scheduler scheduler = new Scheduler(5);

        invoke(scheduler, "markStarted", new Class<?>[]{long.class}, 2_000L);
        invoke(scheduler, "markFailed", new Class<?>[]{long.class, Throwable.class}, 2_010L, new IllegalStateException("boom"));

        Object snapshot = snapshot(scheduler);
        assertEquals(0L, field(snapshot, "completedRuns"));
        assertEquals(1L, field(snapshot, "failedRuns"));
        assertEquals(2_010L, field(snapshot, "lastFailedEpochMs"));
        assertTrue(String.valueOf(field(snapshot, "lastError")).contains("IllegalStateException"));
    }

    @Test
    void emulatorStatsIncludesEveryCoreSchedulerSnapshot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/monitoring/EmulatorStatsService.java"));

        assertTrue(source.contains("getCreditsScheduler().snapshot()"));
        assertTrue(source.contains("getPixelScheduler().snapshot()"));
        assertTrue(source.contains("getPointsScheduler().snapshot()"));
        assertTrue(source.contains("getGotwPointsScheduler().snapshot()"));
        assertTrue(source.contains("subscriptionScheduler.snapshot()"));
    }

    private static Object snapshot(Scheduler scheduler) {
        Method method = assertDoesNotThrow(() -> Scheduler.class.getMethod("snapshot"));
        return assertDoesNotThrow(() -> method.invoke(scheduler));
    }

    private static void invoke(Scheduler scheduler, String name, Class<?>[] types, Object... values) {
        Method method = assertDoesNotThrow(() -> Scheduler.class.getDeclaredMethod(name, types));
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(scheduler, values));
    }

    private static Object field(Object target, String name) {
        Field field = assertDoesNotThrow(() -> target.getClass().getField(name));
        return assertDoesNotThrow(() -> field.get(target));
    }
}
