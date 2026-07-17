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
}
