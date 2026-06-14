package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    void windowsAnsiModeInstallsJansiBeforePrintingStartupHero() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));

        assertTrue(source.contains("AnsiConsole.systemInstall()"),
                "forced ANSI mode must install the Jansi bridge for Windows CMD/System.out");
        assertTrue(source.contains("configureAnsiConsole(styledConsole)"),
                "console bridge must be configured before startupHero is printed");
        assertTrue(source.indexOf("configureAnsiConsole(styledConsole)") < source.indexOf("startupHero(styledConsole)"),
                "Jansi must be installed before writing ANSI startup output");
    }
}
