package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.EmulatorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the test config seam ({@link EmulatorTestSupport}) works without a
 * database, and characterizes {@link ConfigurationManager}'s default handling —
 * including a real quirk in {@code getBoolean} — so future changes are deliberate.
 */
class EmulatorConfigBootstrapTest {

    private ConfigurationManager previous;

    @BeforeEach
    void snapshot() {
        previous = Emulator.getConfig();
    }

    @AfterEach
    void restore() throws Exception {
        EmulatorTestSupport.setConfig(previous);
    }

    @Test
    void installedConfigIsReadableViaEmulatorGetConfig() throws Exception {
        EmulatorTestSupport.installConfig(Map.of(
                "sample.int", "42",
                "sample.flag", "1"));

        assertNotNull(Emulator.getConfig());
        assertEquals(42, Emulator.getConfig().getInt("sample.int", 7));
        assertEquals(7, Emulator.getConfig().getInt("absent.int", 7)); // int default honored
        assertTrue(Emulator.getConfig().getBoolean("sample.flag", false));
    }

    @Test
    void getBooleanReturnsFalseForAbsentKeyRegardlessOfDefault() throws Exception {
        // CHARACTERIZATION of a current quirk: getBoolean(key, default) ignores
        // `default` for an absent key and returns false (the default is only used
        // while loading or on a parse error). Captured, NOT changed — "fixing" it
        // would flip flags such as pathfinder.step.allow.falling for hotels that
        // never set the key, which is a gameplay change that needs a human call.
        EmulatorTestSupport.installConfig(Map.of());

        assertFalse(Emulator.getConfig().getBoolean("absent.bool", true));
    }
}
