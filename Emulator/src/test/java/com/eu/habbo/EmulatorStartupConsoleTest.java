package com.eu.habbo;

import org.junit.jupiter.api.Test;

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
}
