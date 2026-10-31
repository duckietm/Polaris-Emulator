package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Two saves of a wired box ran on the shared pool and could finish in reverse order, keeping the older settings. */
class WiredDataWriteOrderContractTest {

    @Test
    void anOlderSaveNeverOverwritesANewerOne() throws Exception {
        String wired = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionWired.java"));

        int snapshot = wired.indexOf("synchronized (this.wiredDataSnapshotLock) {");
        assertTrue(snapshot > 0 && snapshot < wired.indexOf("version = ++this.wiredDataVersion;"));
        assertTrue(snapshot < wired.indexOf("String wiredDataRaw = this.getWiredData();"));

        int write = wired.indexOf("synchronized (this.wiredDataWriteLock) {");
        int stale = wired.indexOf("if (version <= this.wiredDataWritten) {", write);
        assertTrue(write > 0 && stale > write && stale < wired.indexOf("UPDATE items SET wired_data", write));
        assertTrue(wired.indexOf("this.wiredDataWritten = version;") > wired.indexOf("statement.execute();", write));
    }
}
