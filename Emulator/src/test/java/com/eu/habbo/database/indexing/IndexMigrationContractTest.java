package com.eu.habbo.database.indexing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexMigrationContractTest {
    @Test
    void flywayMigrationCoversTheContractAndNeverDropsIndexes() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260718190000__query_index_contract.sql"));
        IndexContract contract = IndexContractLoader.load(getClass().getClassLoader());

        assertTrue(migration.contains("information_schema.STATISTICS"));
        assertTrue(migration.contains("PREPARE polaris_index_statement"));
        assertFalse(migration.toUpperCase().contains("DROP INDEX"));
        for (IndexRequirement requirement : contract.requirements()) {
            assertTrue(migration.contains("`" + requirement.name() + "`"),
                    () -> "Flyway migration is missing " + requirement.displayName());
        }
    }
}
