package com.eu.habbo.database.indexing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexMigrationContractTest {
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void flywayMigrationCoversTheContractAndNeverDropsIndexes() throws Exception {
        String migration = Files.readString(MIGRATIONS.resolve("V20260718190000__query_index_contract.sql"));

        assertTrue(migration.contains("information_schema.STATISTICS"));
        assertTrue(migration.contains("PREPARE polaris_index_statement"));
        assertFalse(migration.toUpperCase().contains("DROP INDEX"));
    }

    @Test
    void everyContractIndexIsCreatedByAMigrationAndNeverDropped() throws Exception {
        IndexContract contract = IndexContractLoader.load(getClass().getClassLoader());
        String all;
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            all = files.filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .map(IndexMigrationContractTest::read)
                    .collect(Collectors.joining("\n"));
        }
        String drops = all.lines()
                .filter(line -> line.toUpperCase().contains("DROP INDEX"))
                .collect(Collectors.joining("\n"));

        for (IndexRequirement requirement : contract.requirements()) {
            assertTrue(all.contains("`" + requirement.name() + "`"),
                    () -> "No Flyway migration creates " + requirement.displayName());
            assertFalse(drops.contains("`" + requirement.name() + "`"),
                    () -> "A Flyway migration drops " + requirement.displayName());
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
