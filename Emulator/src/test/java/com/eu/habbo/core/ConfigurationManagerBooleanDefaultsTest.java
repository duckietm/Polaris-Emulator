package com.eu.habbo.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationManagerBooleanDefaultsTest {

    @TempDir
    Path tempDir;

    @Test
    void absentBooleanUsesTheCallersDefault() throws Exception {
        Path config = Files.createFile(tempDir.resolve("config.ini"));
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        assertTrue(manager.getBoolean("missing.default.true", true));
        assertFalse(manager.getBoolean("missing.default.false", false));
    }

    @Test
    void invalidBooleanUsesTheCallersDefault() throws Exception {
        Path config = tempDir.resolve("config.ini");
        Files.writeString(config, "invalid.boolean=perhaps");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        assertTrue(manager.getBoolean("invalid.boolean", true));
        assertFalse(manager.getBoolean("invalid.boolean", false));
    }

    @Test
    void booleanValuesIgnoreCaseAndSurroundingWhitespace() throws Exception {
        Path config = tempDir.resolve("config.ini");
        Files.writeString(config, "enabled= TRUE \ndisabled= 0 ");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        assertTrue(manager.getBoolean("enabled", false));
        assertFalse(manager.getBoolean("disabled", true));
    }
}
