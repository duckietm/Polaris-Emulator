package com.eu.habbo.tools.furni;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniConsistencyCliTest {
    @Test
    void helpDocumentsFileAndDatabaseModes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int result = FurniConsistencyCli.run(new String[]{"--help"}, new PrintStream(output), System.err);

        assertEquals(0, result);
        assertTrue(output.toString().contains("--items"));
        assertTrue(output.toString().contains("--jdbc-url"));
        assertTrue(output.toString().contains("--report"));
    }
}
