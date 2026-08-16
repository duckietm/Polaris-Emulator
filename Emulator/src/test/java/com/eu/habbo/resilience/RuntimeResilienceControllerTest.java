package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimeResilienceControllerTest {

    @Test
    void entersDegradedOnlyAfterTheConfiguredConsecutivePressureWindows() {
        RuntimeResilienceController controller = controller(RuntimeResilienceController.Mode.ENFORCE);

        controller.record(RuntimeResilienceController.Pressure.DEGRADED);
        controller.record(RuntimeResilienceController.Pressure.DEGRADED);
        assertEquals(RuntimeResilienceController.State.NORMAL, controller.snapshot().state());

        controller.record(RuntimeResilienceController.Pressure.DEGRADED);
        assertEquals(RuntimeResilienceController.State.DEGRADED, controller.snapshot().state());
    }

    @Test
    void criticalPressureEscalatesAndHealthyWindowsRecoverInTwoStablePhases() {
        RuntimeResilienceController controller = controller(RuntimeResilienceController.Mode.ENFORCE);

        controller.record(RuntimeResilienceController.Pressure.CRITICAL);
        controller.record(RuntimeResilienceController.Pressure.CRITICAL);
        assertEquals(RuntimeResilienceController.State.CRITICAL, controller.snapshot().state());

        record(controller, RuntimeResilienceController.Pressure.HEALTHY, 3);
        assertEquals(RuntimeResilienceController.State.RECOVERING, controller.snapshot().state());

        record(controller, RuntimeResilienceController.Pressure.HEALTHY, 3);
        assertEquals(RuntimeResilienceController.State.NORMAL, controller.snapshot().state());
    }

    @Test
    void enforcedCriticalStateNeverBlocksEssentialWork() {
        RuntimeResilienceController controller = criticalController(RuntimeResilienceController.Mode.ENFORCE);

        assertEquals(
                RuntimeResilienceController.Action.ALLOW,
                controller.admit(RuntimeResilienceController.WorkClass.ESSENTIAL).effectiveAction());
        assertEquals(
                RuntimeResilienceController.Action.REJECT,
                controller.admit(RuntimeResilienceController.WorkClass.INTERACTIVE).effectiveAction());
        assertEquals(
                RuntimeResilienceController.Action.DEFER,
                controller.admit(RuntimeResilienceController.WorkClass.BACKGROUND).effectiveAction());
    }

    @Test
    void observeModeRecordsTheRecommendationWithoutChangingRuntimeBehavior() {
        RuntimeResilienceController controller = criticalController(RuntimeResilienceController.Mode.OBSERVE);

        RuntimeResilienceController.Admission admission =
                controller.admit(RuntimeResilienceController.WorkClass.INTERACTIVE);

        assertEquals(RuntimeResilienceController.Action.REJECT, admission.recommendedAction());
        assertEquals(RuntimeResilienceController.Action.ALLOW, admission.effectiveAction());
        assertEquals(1L, controller.snapshot().wouldReject());
        assertEquals(0L, controller.snapshot().rejected());
    }

    @Test
    void renewedPressureCancelsRecoveryWithoutWaitingForAnotherEscalationWindow() {
        RuntimeResilienceController controller = criticalController(RuntimeResilienceController.Mode.ENFORCE);
        record(controller, RuntimeResilienceController.Pressure.HEALTHY, 3);
        assertEquals(RuntimeResilienceController.State.RECOVERING, controller.snapshot().state());

        controller.record(RuntimeResilienceController.Pressure.DEGRADED);

        assertEquals(RuntimeResilienceController.State.DEGRADED, controller.snapshot().state());
    }

    private static RuntimeResilienceController controller(RuntimeResilienceController.Mode mode) {
        return new RuntimeResilienceController(mode, new RuntimeResilienceController.Thresholds(3, 2, 3));
    }

    private static RuntimeResilienceController criticalController(RuntimeResilienceController.Mode mode) {
        RuntimeResilienceController controller = controller(mode);
        record(controller, RuntimeResilienceController.Pressure.CRITICAL, 2);
        return controller;
    }

    private static void record(
            RuntimeResilienceController controller,
            RuntimeResilienceController.Pressure pressure,
            int count) {
        for (int index = 0; index < count; index++) {
            controller.record(pressure);
        }
    }
}
