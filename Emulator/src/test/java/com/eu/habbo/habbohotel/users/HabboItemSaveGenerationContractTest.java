package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * An item save cleared needsUpdate after its query, dropping a change made while it wrote, and two saves of the same
 * item could run at once, so an older UPDATE could land last.
 */
class HabboItemSaveGenerationContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/users/HabboItem.java");

    @Test
    void savesOfOneItemRunOneAtATime() throws Exception {
        String source = Files.readString(SOURCE);
        String run = source.substring(source.indexOf("public void run() {"));

        assertTrue(run.substring(0, 120).contains("synchronized (this.saveLock)"));
    }

    @Test
    void aChangeMadeDuringASaveKeepsTheItemDirty() throws Exception {
        String source = Files.readString(SOURCE);
        String mark = source.substring(source.indexOf("public void needsUpdate(boolean value)"));

        assertTrue(mark.substring(0, 160).contains("if (value) this.saveGeneration.incrementAndGet();"));
        assertTrue(source.contains("long generation = this.saveGeneration.get();"));
        assertTrue(source.contains("if (this.saveGeneration.get() == generation) this.needsUpdate = false;"));
        assertFalse(source.contains("}\n\n                this.needsUpdate = false;\n            }"));
    }
}
