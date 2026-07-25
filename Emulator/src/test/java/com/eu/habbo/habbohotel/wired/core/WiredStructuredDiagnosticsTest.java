package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredStructuredDiagnosticsTest {

    @Test
    void disabledDiagnosticsDoNotReachTheSink() {
        List<Object[]> events = new ArrayList<>();
        WiredStructuredDiagnostics diagnostics =
                new WiredStructuredDiagnostics(() -> false, (format, arguments) -> events.add(arguments));

        diagnostics.execution(7, 8, WiredEvent.Type.USER_SAYS, 2, 11L, WiredStructuredDiagnostics.Outcome.EXECUTED);

        assertTrue(events.isEmpty());
    }

    @Test
    void executionEventContainsOnlyBoundedIdentifiersAndOutcome() {
        List<String> formats = new ArrayList<>();
        List<Object[]> events = new ArrayList<>();
        WiredStructuredDiagnostics diagnostics = new WiredStructuredDiagnostics(() -> true, (format, arguments) -> {
            formats.add(format);
            events.add(arguments);
        });

        diagnostics.execution(-7, -8, WiredEvent.Type.USER_SAYS, -2, -11L, WiredStructuredDiagnostics.Outcome.EXECUTED);

        assertEquals(
                "wired_execution room={} stack={} trigger={} effects={} duration_ms={} outcome={}", formats.getFirst());
        assertArrayEquals(
                new Object[] {0, 0, WiredEvent.Type.USER_SAYS, 0, 0L, WiredStructuredDiagnostics.Outcome.EXECUTED},
                events.getFirst());
    }
}
