package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupPhasesTest {

    @Test
    void phasesRunInDeclaredDependencyOrder() throws Exception {
        List<String> calls = new ArrayList<>();

        boolean started = StartupPhases.run(List.of(
                phase("configuration", calls),
                phase("database", calls),
                phase("plugins", calls),
                phase("hotel", calls),
                phase("network", calls)));

        assertTrue(started);
        assertEquals(List.of("configuration", "database", "plugins", "hotel", "network"), calls);
    }

    @Test
    void intentionalToolModeStopsBeforeLaterPhases() throws Exception {
        List<String> calls = new ArrayList<>();

        boolean started = StartupPhases.run(List.of(
                phase("configuration", calls),
                new StartupPhases.Phase("database", () -> {
                    calls.add("database");
                    return false;
                }),
                phase("plugins", calls)));

        assertFalse(started);
        assertEquals(List.of("configuration", "database"), calls);
    }

    @Test
    void failureStopsBeforeDependentPhasesAndPropagates() {
        List<String> calls = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("expected");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> StartupPhases.run(List.of(
                        phase("configuration", calls),
                        new StartupPhases.Phase("database", () -> {
                            calls.add("database");
                            throw failure;
                        }),
                        phase("plugins", calls))));

        assertEquals(failure, thrown);
        assertEquals(List.of("configuration", "database"), calls);
    }

    private static StartupPhases.Phase phase(String name, List<String> calls) {
        return new StartupPhases.Phase(name, () -> {
            calls.add(name);
            return true;
        });
    }
}
