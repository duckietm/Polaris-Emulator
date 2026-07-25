package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WiredRoomDiagnosticsTest {

    @Test
    void rejectedDelayedAdmissionRollsBackAndRecoversAfterAcceptedWorkCompletes() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5);

        assertTrue(diagnostics.tryScheduleDelayedEvent(1_000L, "fixture", 7, "first"));
        assertFalse(diagnostics.tryScheduleDelayedEvent(1_001L, "fixture", 7, "rejected"));
        assertEquals(1, pending(diagnostics, 1_001L));

        diagnostics.completeDelayedEvent();
        assertEquals(0, pending(diagnostics, 1_002L));

        assertTrue(diagnostics.tryScheduleDelayedEvent(1_003L, "fixture", 7, "recovered"));
        assertEquals(1, pending(diagnostics, 1_003L));
    }

    private static int pending(WiredRoomDiagnostics diagnostics, long now) {
        return diagnostics.snapshot(0, 10, 0L, now).getDelayedEventsPending();
    }
}
