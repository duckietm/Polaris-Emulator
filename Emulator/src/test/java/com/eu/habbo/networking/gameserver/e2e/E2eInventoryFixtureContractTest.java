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
        String dump = Files.readString(REPOSITORY.resolve("Database/Default Database/FullDatabase.sql"));

        assertTrue(dump.contains("(18, 18, 'Dining Chair', 'chair_polyfon', 's'"));
    }
}
