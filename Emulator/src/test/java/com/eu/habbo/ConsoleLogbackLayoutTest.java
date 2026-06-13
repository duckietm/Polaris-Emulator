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

        assertTrue(logback.contains("%-22logger{0}"), "console should show compact class names");
        assertTrue(logback.contains("| %msg%n"), "console should leave a clear message column");
        assertFalse(logback.contains("%-36logger{36}"), "wide package loggers waste console space");
    }
}
