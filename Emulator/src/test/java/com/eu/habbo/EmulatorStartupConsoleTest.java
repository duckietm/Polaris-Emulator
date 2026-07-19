package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorStartupConsoleTest {
    @Test
    void consoleInputStopsCleanlyAtEndOfFile() throws Exception {
        List<String> commands = new ArrayList<>();
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        boolean keepRunning;
        try (BufferedReader reader = new BufferedReader(new StringReader(""));
             PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8)) {
            keepRunning = Emulator.processConsoleInput(reader, commands::add, output);
        }

        assertFalse(keepRunning);
        assertTrue(commands.isEmpty());
        assertEquals("", outputBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void consoleInputDispatchesCommandAndKeepsLoopRunning() throws Exception {
        List<String> commands = new ArrayList<>();
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        boolean keepRunning;
        try (BufferedReader reader = new BufferedReader(new StringReader("status\n"));
             PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8)) {
            keepRunning = Emulator.processConsoleInput(reader, commands::add, output);
        }

        assertTrue(keepRunning);
        assertEquals(List.of("status"), commands);
        assertEquals("Waiting for command: " + System.lineSeparator(),
                outputBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void startupHeroUsesUniversalAsciiLayout() {
        String hero = Emulator.startupHero();

        assertTrue(hero.contains("____   ___  _"));
        assertTrue(hero.contains("POLARIS"));
        assertTrue(hero.contains("Version"));
        assertTrue(hero.contains("Build"));
        assertFalse(hero.contains("\u001B["), "startup hero must not require ANSI support");
    }

    @Test
    void startupHeroCanRenderStyledLayoutWhenAnsiIsAvailable() {
        String hero = Emulator.startupHero(true);

        assertTrue(hero.contains("\u001B["), "styled hero should include ANSI colors");
        assertTrue(hero.contains("[OK] POLARIS"));
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
        assertTrue(source.indexOf("registerStartupConfigDefaults();") < source.indexOf("shouldLaunchGui()"),
                "GUI autostart must be registered before the launch decision");
        assertFalse(source.contains("getBoolean(\"gui.enabled\", true)"),
                "GUI must not use a true fallback");
        assertFalse(source.contains("getBoolean(\"gui.enabled\", false)"),
                "legacy gui.enabled must not control startup anymore");
    }
}
