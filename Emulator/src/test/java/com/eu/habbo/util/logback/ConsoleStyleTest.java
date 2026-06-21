package com.eu.habbo.util.logback;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleStyleTest {
    @Test
    void formatsLevelWithIconAndColorWhenStyled() {
        String formatted = ConsoleStyle.level(Level.WARN, true);

        assertTrue(formatted.contains("\u001B["));
        assertTrue(formatted.contains("[!] WARN "));
        assertTrue(formatted.endsWith("\u001B[0m"));
    }

    @Test
    void formatsLevelAsPlainTextWhenNotStyled() {
        assertEquals("WARN ", ConsoleStyle.level(Level.WARN, false));
    }

    @Test
    void formatsLoggerWithColorWhenStyled() {
        String formatted = ConsoleStyle.logger("com.eu.habbo.networking.Server", true);

        assertTrue(formatted.contains("\u001B["));
        assertTrue(formatted.contains("Server"));
        assertTrue(formatted.endsWith("\u001B[0m"));
    }

    @Test
    void keepsLoggerPlainAndCompactWhenNotStyled() {
        assertEquals("Server                ", ConsoleStyle.logger("com.eu.habbo.networking.Server", false));
    }

    @Test
    void honorsPlainOverrideEvenInWindowsTerminal() {
        assertFalse(ConsoleStyle.isEnabled(
                Map.of("WT_SESSION", "abc123"),
                true,
                "Windows 11",
                "plain"));
    }
}
