package com.eu.habbo.stress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StressTransientLifecycleContractTest {
    private static final Path MANAGER = Path.of("src/main/java/com/eu/habbo/stress/StressRunManager.java");
    private static final Path PACKAGE = Path.of("src/main/java/com/eu/habbo/stress");

    @Test
    void injectedEntitiesNeverUseDatabaseBackedFactories() throws Exception {
        String source = Files.readString(MANAGER);
        String transientSources = Files.readString(PACKAGE.resolve("StressTransientItem.java"))
                + Files.readString(PACKAGE.resolve("StressTransientRoller.java"))
                + Files.readString(PACKAGE.resolve("StressWiredTrigger.java"))
                + Files.readString(PACKAGE.resolve("StressWiredEffect.java"));

        assertTrue(source.contains("new Bot("));
        assertTrue(source.contains("new StressTransientItem("));
        assertTrue(source.contains("new StressTransientRoller("));
        assertTrue(source.contains("new StressWiredTrigger("));
        assertTrue(source.contains("new StressWiredEffect("));
        assertTrue(transientSources.contains("Stress entities are deliberately never persisted"));
        assertFalse(transientSources.contains("com.eu.habbo.Emulator"));
        assertFalse(transientSources.contains("PreparedStatement"));
        assertFalse(
                source.contains("getItemManager().createItem("),
                "stress furniture must not create inventory/database rows");
        assertFalse(source.contains("getRoomManager().createRoom("), "stress runs must use an existing room");
        assertFalse(source.contains("getDatabase()"), "stress lifecycle must not access the database");
        assertFalse(source.contains("PreparedStatement"), "stress lifecycle must not execute SQL");
        assertFalse(source.contains(".setRollerSpeed("), "stress runs must not persist a room roller-speed change");
    }

    @Test
    void cleanupRemovesRunOwnedEntitiesAndRestoresTheRoomFlag() throws Exception {
        String source = Files.readString(MANAGER);

        assertTrue(source.contains("removeBot(bot)"));
        assertTrue(source.contains("removeHabboItem(item)"));
        assertTrue(source.contains("cancel(run.wiredTask)"));
        assertTrue(source.contains("synchronized (run.wiredExecutionLock)"));
        assertTrue(source.contains("room.getItemManager().tileCache.clear()"));
        assertTrue(source.contains("room.setTransientRollerSpeedOverride(run.previousRollerSpeedOverride)"));
        assertTrue(source.contains("room.preventUncaching = run.previousPreventUncaching"));
        assertTrue(source.contains("run.cleanupStarted.compareAndSet(false, true)"));
    }
}
