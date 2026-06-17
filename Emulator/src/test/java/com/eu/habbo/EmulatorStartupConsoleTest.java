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

    @Test
    void registersGuiEnabledBeforeReadingIt() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));

        assertTrue(source.contains("register(\"gui.enabled\", \"0\")"),
                "gui.enabled must be registered disabled by default so it does not log missing config errors or start the UI unexpectedly");
        assertTrue(source.contains("register(\"gui.autostart.enabled\", \"0\")"),
                "GUI autostart must use a new disabled-by-default key so old gui.enabled=1 settings do not launch the current UI");
        assertTrue(source.indexOf("register(\"gui.autostart.enabled\", \"0\")") < source.indexOf("shouldLaunchGui()"),
                "GUI autostart must be registered before the launch decision");
        assertFalse(source.contains("getBoolean(\"gui.enabled\", true)"),
                "GUI must not use a true fallback");
        assertFalse(source.contains("getBoolean(\"gui.enabled\", false)"),
                "legacy gui.enabled must not control startup anymore");
    }
}
