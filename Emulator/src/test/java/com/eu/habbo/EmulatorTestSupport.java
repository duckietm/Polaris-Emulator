package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Test-only seam for the Emulator config singleton.
 *
 * <p>Many classes read {@code Emulator.getConfig()} (some in static
 * initializers), which normally requires a full emulator bootstrap. This helper
 * builds a {@link ConfigurationManager} from an in-memory {@code .ini} (no
 * database — {@code loadFromDatabase()} only runs once {@code loaded == true},
 * which never happens here) and installs it into the private static
 * {@code Emulator.config} field via reflection.
 *
 * <p>Production code is untouched; this lives in the test source set only. It is
 * the minimal first step toward a fuller Emulator test harness.
 */
public final class EmulatorTestSupport {

    private EmulatorTestSupport() {
    }

    /**
     * Build a DB-free {@link ConfigurationManager} seeded with the given keys and
     * install it as the active emulator config.
     */
    public static ConfigurationManager installConfig(Map<String, String> entries) throws Exception {
        Path ini = Files.createTempFile("amx-test-config", ".ini");
        ini.toFile().deleteOnExit();

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        Files.writeString(ini, sb.toString());

        ConfigurationManager manager = new ConfigurationManager(ini.toString());
        setConfig(manager);
        return manager;
    }

    /**
     * Install (or, with {@code null}, clear) the {@code Emulator.config} singleton.
     * Tests should snapshot the previous value and restore it afterwards.
     */
    public static void setConfig(ConfigurationManager manager) throws Exception {
        Field field = Emulator.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, manager);
    }
}
