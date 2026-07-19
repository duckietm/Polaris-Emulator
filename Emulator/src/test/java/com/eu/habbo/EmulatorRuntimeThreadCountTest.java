package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmulatorRuntimeThreadCountTest {

    @TempDir
    Path tempDir;

    @Test
    void usesConfiguredRuntimeThreadCount() throws Exception {
        Path configFile = tempDir.resolve("config.ini");
        Files.writeString(configFile, "runtime.threads=12");
        ConfigurationManager config = new ConfigurationManager(configFile.toString());

        assertEquals(12, Emulator.runtimeThreadCount(config));
    }
}
