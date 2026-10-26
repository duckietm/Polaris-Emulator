package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * In enforce mode CRITICAL ignored DEGRADED samples, so only an unbroken run of HEALTHY samples could
 * leave it. DEGRADED fired on live heap use (garbage included) and on every scheduled task, even ones
 * booked for later: after one short spike the hotel kept refusing logins indefinitely.
 */
class ResilienceCriticalRecoveryTest {

    private static RuntimeResilienceController critical() {
        RuntimeResilienceController controller = new RuntimeResilienceController(
                RuntimeResilienceController.Mode.ENFORCE, new RuntimeResilienceController.Thresholds(2, 2, 3));
        controller.record(RuntimeResilienceController.Pressure.CRITICAL);
        controller.record(RuntimeResilienceController.Pressure.CRITICAL);
        assertEquals(
                RuntimeResilienceController.State.CRITICAL,
                controller.snapshot().state());
        return controller;
    }

    @Test
    void sustainedDegradedPressureStepsDownFromCritical() {
        RuntimeResilienceController controller = critical();

        controller.record(RuntimeResilienceController.Pressure.DEGRADED);
        assertEquals(
                RuntimeResilienceController.State.CRITICAL,
                controller.snapshot().state());
        controller.record(RuntimeResilienceController.Pressure.DEGRADED);

        assertEquals(
                RuntimeResilienceController.State.DEGRADED,
                controller.snapshot().state());
        assertEquals(
                RuntimeResilienceController.Action.ALLOW,
                controller
                        .admit(RuntimeResilienceController.WorkClass.INTERACTIVE)
                        .effectiveAction(),
                "logins are admitted again once the hotel is only degraded");
    }

    @Test
    void criticalPressureStillHoldsCritical() {
        RuntimeResilienceController controller = critical();

        controller.record(RuntimeResilienceController.Pressure.DEGRADED);
        controller.record(RuntimeResilienceController.Pressure.CRITICAL);
        controller.record(RuntimeResilienceController.Pressure.DEGRADED);

        assertEquals(
                RuntimeResilienceController.State.CRITICAL,
                controller.snapshot().state());
    }

    @Test
    void heapUseIsMeasuredAfterCollection() {
        long max = 1_000L;
        List<MemoryUsage> afterCollection = List.of(new MemoryUsage(0, 0, 100, 400), new MemoryUsage(0, 200, 300, 600));

        assertEquals(0.2D, RuntimeResilienceRuntime.heapUtilization(afterCollection, max, 900L), 1e-9);
    }

    @Test
    void beforeAnyCollectionTheLiveFigureIsUsed() {
        List<MemoryUsage> neverCollected = Arrays.asList(new MemoryUsage(0, 0, 100, 400), null);

        assertEquals(0.9D, RuntimeResilienceRuntime.heapUtilization(neverCollected, 1_000L, 900L), 1e-9);
        assertEquals(0D, RuntimeResilienceRuntime.heapUtilization(List.of(), 0L, 900L));
    }

    @Test
    void onlyOverdueScheduledTasksCount() {
        List<Runnable> queue = new ArrayList<>();
        queue.add(new Task(-5));
        queue.add(new Task(0));
        queue.add(new Task(TimeUnit.MINUTES.toNanos(10)));
        queue.add(new Task(TimeUnit.HOURS.toNanos(1)));
        queue.add(() -> {});

        assertEquals(3, RuntimeResilienceRuntime.overdueTasks(queue));
    }

    private record Task(long delayNanos) implements Runnable, Delayed {
        @Override
        public void run() {}

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.delayNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(this.delayNanos, other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
