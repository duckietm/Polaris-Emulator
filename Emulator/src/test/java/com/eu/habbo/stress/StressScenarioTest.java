package com.eu.habbo.stress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StressScenarioTest {
    private final StressLimits limits = new StressLimits(5_000, 100_000, 50_000, 50_000, 100, 200_000, 10_000, 3_600);

    @Test
    void acceptsTheDocumentedMixedScenario() {
        StressScenario scenario = new StressScenario(41, 2_000, 30_000, 10_000, 25_000, 2, 0, 2_500, 120, 42L, true);

        assertDoesNotThrow(() -> scenario.validate(this.limits));
    }

    @Test
    void requiresWorkAndBotsForChat() {
        IllegalArgumentException empty = assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 0, 0, 0, 0, 0, 0, 120, 42L, false).validate(this.limits));
        IllegalArgumentException chatWithoutBots = assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 1, 0, 0, 0, 0, 1, 120, 42L, false).validate(this.limits));
        IllegalArgumentException wiredEventsWithoutStacks = assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 1, 0, 0, 1, 0, 0, 120, 42L, false).validate(this.limits));

        assertEquals("scenario must contain at least one workload", empty.getMessage());
        assertEquals("chat requires at least one bot", chatWithoutBots.getMessage());
        assertEquals("wired events require at least one wired stack", wiredEventsWithoutStacks.getMessage());
    }

    @Test
    void enforcesEveryConfiguredSafetyLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 5_001, 0, 0, 0, 0, 0, 0, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 100_001, 0, 0, 0, 0, 0, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 0, 50_001, 0, 0, 0, 0, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 0, 0, 50_001, 0, 0, 0, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 0, 0, 0, 1, 101, 0, 0, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 1, 0, 0, 0, 0, 0, 10_001, 1, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 1, 0, 0, 0, 0, 0, 0, 3_601, 1L, false).validate(this.limits));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StressScenario(41, 5_000, 100_000, 50_000, 50_000, 0, 0, 0, 1, 1L, false)
                        .validate(this.limits));
    }
}
