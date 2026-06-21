package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLogbackLayoutTest {
    @Test
    void consolePatternKeepsStartupMessagesReadable() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback.xml"));

        assertTrue(logback.contains("morningstarLevel"), "console should use the adaptive level formatter");
        assertTrue(logback.contains("morningstarLogger"), "console should use the adaptive logger formatter");
        assertTrue(logback.contains("| %msg%n"), "console should leave a clear message column");
        assertFalse(logback.contains("%-36logger{36}"), "wide package loggers waste console space");
    }
}
