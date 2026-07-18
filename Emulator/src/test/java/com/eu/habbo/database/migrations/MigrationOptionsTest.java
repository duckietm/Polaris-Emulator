package com.eu.habbo.database.migrations;

import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationOptionsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void commandLineOverridesEnvironmentAndConfiguration() throws IOException {
        MigrationOptions options = MigrationOptions.resolve(
                config("off", 12),
                new String[]{"--migrations=apply", "--migrations-only"},
                Map.of("DB_MIGRATIONS_MODE", "validate"));

        assertEquals(MigrationMode.APPLY, options.mode());
        assertTrue(options.migrationsOnly());
        assertEquals(12, options.lockTimeoutSeconds());
    }

    @Test
    void environmentOverridesConfiguration() throws IOException {
        MigrationOptions options = MigrationOptions.resolve(
                config("off", 10),
                new String[0],
                Map.of("DB_MIGRATIONS_MODE", "validate"));

        assertEquals(MigrationMode.VALIDATE, options.mode());
        assertFalse(options.migrationsOnly());
    }

    @Test
    void configurationOverridesDefault() throws IOException {
        assertEquals(
                MigrationMode.OFF,
                MigrationOptions.resolve(config("off", 10), new String[0], Map.of()).mode());
    }

    @Test
    void defaultsToValidateAndTenSecondLock() throws IOException {
        ConfigurationManager config = config(null, null);
        assertNull(config.getValueIfPresent("db.migrations.mode"));
        assertNull(config.getValueIfPresent("db.migrations.lock_timeout_seconds"));

        MigrationOptions options = MigrationOptions.resolve(config, new String[0], Map.of());

        assertEquals(MigrationMode.VALIDATE, options.mode());
        assertEquals(10, options.lockTimeoutSeconds());
    }

    @Test
    void rejectsUnknownArguments() throws IOException {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.resolve(config("validate", 10), new String[]{"--unknown"}, Map.of()));

        assertEquals("Unknown command-line argument: --unknown", error.getMessage());
    }

    @Test
    void rejectsLockTimeoutOutsideBoundedRange() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.resolve(config("validate", 0), new String[0], Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> MigrationOptions.resolve(config("validate", 61), new String[0], Map.of()));
    }

    private ConfigurationManager config(String mode, Integer lockTimeoutSeconds) throws IOException {
        Path path = temporaryDirectory.resolve("config-" + System.nanoTime() + ".ini");
        StringBuilder content = new StringBuilder();
        if (mode != null) content.append("db.migrations.mode=").append(mode).append('\n');
        if (lockTimeoutSeconds != null) {
            content.append("db.migrations.lock_timeout_seconds=").append(lockTimeoutSeconds).append('\n');
        }
        Files.writeString(path, content);
        return new ConfigurationManager(path.toString());
    }
}
