package com.eu.habbo.networking.gameserver.e2e;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class E2eInventoryFixtureContractTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();

    @Test
    void seedsOneOwnedFloorItemInInventory() throws IOException {
        String seed = Files.readString(REPOSITORY.resolve("e2e/seed.sql"));

        assertTrue(seed.contains("DELETE FROM items WHERE id = 900004"));
        assertTrue(seed.contains("INSERT INTO items"));
        assertTrue(seed.contains("900004, 900001, 0, 18"));
    }

    @Test
    void canonicalDumpContainsTheSelectedFloorItemBase() throws IOException {
        String dump = Files.readString(REPOSITORY.resolve(
                "Emulator/src/main/resources/db/migration/V20260518000000__base_database.sql"));

        assertTrue(dump.contains("(18, 18, 'Dining Chair', 'chair_polyfon', 's'"));
    }

    @Test
    void persistenceScriptsCheckTheExactOwnedInventoryState() throws IOException {
        String shell = Files.readString(REPOSITORY.resolve("e2e/verify-inventory-state.sh"));
        String powershell = Files.readString(REPOSITORY.resolve("e2e/verify-inventory-state.ps1"));

        for (String script : new String[]{shell, powershell}) {
            assertTrue(script.contains("900004"));
            assertTrue(script.contains("900001:0"));
            assertTrue(script.contains("user_id"));
            assertTrue(script.contains("room_id"));
        }
    }

    @Test
    void preparationScriptsOnlyResetDisposableSchemas() throws IOException {
        String shell = Files.readString(REPOSITORY.resolve("e2e/prepare-database.sh"));
        String powershell = Files.readString(REPOSITORY.resolve("e2e/prepare-database.ps1"));

        for (String script : new String[]{shell, powershell}) {
            assertTrue(script.contains("polaris_e2e_"));
            assertTrue(script.contains("DROP DATABASE IF EXISTS"));
            assertTrue(script.contains("CREATE DATABASE"));
        }
    }
}
