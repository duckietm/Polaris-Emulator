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

    @Test
    void aBoxLogLineLandsInTheHistoryAtItsOwnLevel() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5);

        diagnostics.recordWiredLog(1_000L, WiredRoomDiagnostics.Severity.DEBUG, "door opened", "wf_act_log", 9);
        diagnostics.recordWiredLog(1_001L, WiredRoomDiagnostics.Severity.ERROR, "boss escaped", "wf_act_log", 9);

        var history = diagnostics.snapshot(0, 10, 0L, 1_002L).getHistory();
        assertEquals(2, history.size());
        // Newest first; the level is AIR's number (0 info, 1 warn, 2 error, 3 debug), not the enum's place.
        assertEquals("boss escaped", history.get(0).getReason());
        assertEquals(2, history.get(0).getSeverity().getLogLevel());
        assertEquals(3, history.get(1).getSeverity().getLogLevel());
        assertEquals(WiredRoomDiagnostics.Type.WIRED_LOG, history.get(1).getType());
    }

    @Test
    void aChattyLogBoxCannotPushEngineEntriesOutOfTheHistory() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1_000, 100, 1, 50, 150, 70, 5);
        diagnostics.recordKilled(1_000L, "loop", "wf_trg_periodically", 3);

        for (int i = 0; i < 500; i++) {
            diagnostics.recordWiredLog(1_001L + i, WiredRoomDiagnostics.Severity.INFO, "tick " + i, "wf_act_log", 9);
        }

        var history = diagnostics.snapshot(0, 10, 0L, 2_000L).getHistory();
        assertTrue(history.stream().anyMatch(entry -> entry.getType() == WiredRoomDiagnostics.Type.KILLED));
        assertEquals("tick 499", history.get(0).getReason());
    }

    @Test
    void logLevelsMapBothWays() {
        for (WiredRoomDiagnostics.Severity severity : WiredRoomDiagnostics.Severity.values()) {
            assertEquals(severity, WiredRoomDiagnostics.Severity.fromLogLevel(severity.getLogLevel()));
        }

        assertEquals(WiredRoomDiagnostics.Severity.INFO, WiredRoomDiagnostics.Severity.fromLogLevel(42));
    }

    private static int pending(WiredRoomDiagnostics diagnostics, long now) {
        return diagnostics.snapshot(0, 10, 0L, now).getDelayedEventsPending();
    }
}
