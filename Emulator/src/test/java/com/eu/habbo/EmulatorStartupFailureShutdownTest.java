package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorStartupFailureShutdownTest {
    @Test
    void shutdownDoesNotSaveConfigurationWithoutAnInitializedDatabase() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));

        assertTrue(source.contains(
                "Emulator.config != null && Emulator.database != null"));
    }
}
