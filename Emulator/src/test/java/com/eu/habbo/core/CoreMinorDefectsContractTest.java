package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Small core defects: the error log's build hash, the motto easter egg outside a room, the thread count, the source link. */
class CoreMinorDefectsContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path));
    }

    @Test
    void errorLogRecordsTheBuild() throws Exception {
        assertTrue(source("core/ErrorLog.java").contains("this.buildHash = Emulator.build;"));
    }

    @Test
    void mottoEasterEggNeedsARoom() throws Exception {
        String easter = source("core/Easter.java");

        int guard = easter.indexOf("if (room == null) {");
        assertTrue(guard > 0 && guard < easter.indexOf("room.sendComposer("));
    }

    @Test
    void startupReportsTheConfiguredThreadsAndTheRealSource() throws Exception {
        String emulator = source("Emulator.java");

        assertTrue(emulator.contains("int threads = Emulator.getConfig().getInt(\"runtime.threads\");"));
        assertTrue(emulator.contains("github.com/duckietm/Polaris-Emulator"));
        assertFalse(emulator.contains("Nitro-Cool-UI-Renderer"));
    }
}
