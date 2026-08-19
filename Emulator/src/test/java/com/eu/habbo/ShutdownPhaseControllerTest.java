package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShutdownPhaseControllerTest {

    @Test
    void advancesMonotonicallyAndRejectsSkippedOrRepeatedPhases() {
        ShutdownPhaseController controller = new ShutdownPhaseController();

        assertEquals(ShutdownPhase.RUNNING, controller.current());
        assertTrue(controller.advanceTo(ShutdownPhase.ANNOUNCE));
        assertFalse(controller.advanceTo(ShutdownPhase.ANNOUNCE));
        assertFalse(controller.advanceTo(ShutdownPhase.DRAIN));
        assertTrue(controller.advanceTo(ShutdownPhase.QUIESCE));
        assertTrue(controller.advanceTo(ShutdownPhase.DRAIN));
        assertTrue(controller.advanceTo(ShutdownPhase.CHECKPOINT));
        assertTrue(controller.advanceTo(ShutdownPhase.STOP));
        assertEquals(ShutdownPhase.STOP, controller.current());
    }
}
