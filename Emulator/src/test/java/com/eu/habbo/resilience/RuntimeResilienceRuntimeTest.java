package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeResilienceRuntimeTest {

    @AfterEach
    void resetRuntime() {
        RuntimeResilienceRuntime.resetForTests();
    }

    @Test
    void remainsFailSafeBeforeBootstrapInstallation() {
        assertEquals(
                RuntimeResilienceController.Action.ALLOW,
                RuntimeResilienceRuntime.admit(RuntimeResilienceController.WorkClass.BACKGROUND)
                        .effectiveAction());
    }

    @Test
    void installedServiceDrivesSharedAdmissionDecisions() {
        RuntimeResilienceController controller = new RuntimeResilienceController(
                RuntimeResilienceController.Mode.ENFORCE, new RuntimeResilienceController.Thresholds(1, 1, 1));
        RuntimeResilienceService service = new RuntimeResilienceService(
                controller,
                new RuntimePressureAssessor(new RuntimePressureAssessor.Thresholds(0.75D, 0.95D, 2)),
                () -> new RuntimePressureAssessor.Sample(1D, 2, 1D, 1D, 1D, 1D, true),
                1L,
                System::nanoTime);
        DependencyCircuitBreakers breakers = new DependencyCircuitBreakers(
                RuntimeResilienceController.Mode.ENFORCE,
                new DependencyCircuitBreakers.Settings(50F, 2, 2, Duration.ofSeconds(30), 1));

        RuntimeResilienceRuntime.install(service, breakers);

        assertEquals(
                RuntimeResilienceController.Action.REJECT,
                RuntimeResilienceRuntime.admit(RuntimeResilienceController.WorkClass.INTERACTIVE)
                        .effectiveAction());
        assertEquals(
                RuntimeResilienceController.State.CRITICAL,
                RuntimeResilienceRuntime.status().controller().state());
    }
}
