package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EmulatorConsoleEofTest {

    @Test
    void endOfInputStopsTheConsoleLoop() throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(""));

        assertFalse(Emulator.readConsoleCommand(reader));
    }
}
