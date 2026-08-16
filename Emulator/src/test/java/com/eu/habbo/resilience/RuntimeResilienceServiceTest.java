package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RuntimeResilienceServiceTest {

    @Test
    void samplesImmediatelyWhenTheMonotonicClockStartsNegative() {
        AtomicInteger samples = new AtomicInteger();
        AtomicLong clock = new AtomicLong(-100L);
        RuntimeResilienceService service = service(
                () -> {
                    samples.incrementAndGet();
                    return new RuntimePressureAssessor.Sample(1D, 2, 1D, 1D, 1D, 1D, true);
                },
                clock);

        RuntimeResilienceController.Admission admission =
                service.admit(RuntimeResilienceController.WorkClass.INTERACTIVE);

        assertEquals(1, samples.get());
        assertEquals(RuntimeResilienceController.Action.REJECT, admission.effectiveAction());
    }

    @Test
    void refreshesPressureAtMostOncePerSamplingInterval() {
        AtomicInteger samples = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        RuntimeResilienceService service = service(
                () -> {
                    samples.incrementAndGet();
                    return healthySample();
                },
                clock);

        service.admit(RuntimeResilienceController.WorkClass.ESSENTIAL);
        service.admit(RuntimeResilienceController.WorkClass.BACKGROUND);
        assertEquals(1, samples.get());

        clock.set(1_000_000_000L);
        service.snapshot();
        assertEquals(2, samples.get());
    }

    @Test
    void telemetryFailureDegradesConservativelyWithoutBlockingEssentialWork() {
        AtomicLong clock = new AtomicLong();
        RuntimeResilienceService service = service(
                () -> {
                    throw new IllegalStateException("metrics unavailable");
                },
                clock);

        RuntimeResilienceController.Admission admission =
                service.admit(RuntimeResilienceController.WorkClass.ESSENTIAL);

        assertEquals(RuntimeResilienceController.Action.ALLOW, admission.effectiveAction());
        assertEquals(
                RuntimeResilienceController.Pressure.DEGRADED,
                service.snapshot().lastPressure());
        assertEquals(1L, service.snapshot().samplingFailures());
    }

    private static RuntimeResilienceService service(RuntimeResilienceService.PressureSource source, AtomicLong clock) {
        RuntimeResilienceController controller = new RuntimeResilienceController(
                RuntimeResilienceController.Mode.ENFORCE, new RuntimeResilienceController.Thresholds(1, 1, 1));
        RuntimePressureAssessor assessor =
                new RuntimePressureAssessor(new RuntimePressureAssessor.Thresholds(0.75D, 0.95D, 2));
        return new RuntimeResilienceService(controller, assessor, source, 1_000_000_000L, clock::get);
    }

    private static RuntimePressureAssessor.Sample healthySample() {
        return new RuntimePressureAssessor.Sample(0D, 0, 0D, 0D, 0D, 0D, false);
    }
}
