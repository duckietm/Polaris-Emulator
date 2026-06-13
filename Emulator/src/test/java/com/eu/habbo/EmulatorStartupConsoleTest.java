package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorStartupConsoleTest {
    @Test
    void startupHeroUsesUniversalAsciiLayout() {
        String hero = Emulator.startupHero();

        assertTrue(hero.contains("__  __  ___  ____"));
        assertTrue(hero.contains("MORNINGSTAR EXTENDED"));
        assertTrue(hero.contains("Version"));
        assertTrue(hero.contains("Build"));
        assertFalse(hero.contains("\u001B["), "startup hero must not require ANSI support");
    }

    @Test
    void startupHeroCanRenderStyledLayoutWhenAnsiIsAvailable() {
        String hero = Emulator.startupHero(true);

        assertTrue(hero.contains("\u001B["), "styled hero should include ANSI colors");
        assertTrue(hero.contains("[OK] MORNINGSTAR EXTENDED"));
        assertTrue(hero.contains("[JVM]"));
        assertTrue(hero.endsWith("\u001B[0m\n"), "styled hero should reset terminal attributes");
    }

    @Test
    void consoleStyleAutoDetectsWindowsTerminal() {
        assertTrue(Emulator.shouldStyleConsole(
                Map.of("WT_SESSION", "abc123"),
                true,
                "Windows 11",
                "auto"));
    }

    @Test
    void consoleStyleFallsBackWhenOutputIsNotInteractive() {
        assertFalse(Emulator.shouldStyleConsole(
                Map.of("WT_SESSION", "abc123"),
                false,
                "Windows 11",
                "auto"));
    }

    @Test
    void consoleStyleCanBeForcedOff() {
        assertFalse(Emulator.shouldStyleConsole(
                Map.of("WT_SESSION", "abc123"),
                true,
                "Windows 11",
                "plain"));
    }
}
