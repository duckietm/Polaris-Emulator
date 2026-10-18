package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Place Furni failures are counted in the room's wired diagnostics, as warnings. */
class WiredPlacementFailureDiagnosticTest {

    @Test
    void placementFailuresAddUpAsAWarning() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);

        diagnostics.recordPlacementFailure(1_000L, "Place Furni could not place every copy", "place_furni", 7);
        diagnostics.recordPlacementFailure(1_100L, "Place Furni could not place every copy", "place_furni", 7);

        WiredRoomDiagnostics.LogEntry entry = diagnostics.snapshot(0, 10, 0L, 1_200L).getLogs().stream()
                .filter(log -> log.getType() == WiredRoomDiagnostics.Type.PLACEMENT_FAILED)
                .filter(log -> log.getCount() > 0)
                .findFirst()
                .orElseThrow();
        assertEquals(2, entry.getCount());
        assertEquals(WiredRoomDiagnostics.Severity.WARNING, entry.getSeverity());
    }
}
