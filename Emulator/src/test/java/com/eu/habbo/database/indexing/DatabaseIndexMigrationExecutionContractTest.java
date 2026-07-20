package com.eu.habbo.database.indexing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatabaseIndexMigrationExecutionContractTest {

    @Test
    void indexMigrationRunsAsAFailsafeTestcontainersTest() throws Exception {
        Path legacyTest =
                Path.of("src/test/java/com/eu/habbo/database/indexing/" + "DatabaseIndexMigrationIntegrationTest.java");
        Path integrationTest =
                Path.of("src/test/java/com/eu/habbo/database/indexing/" + "DatabaseIndexMigrationIT.java");

        assertFalse(Files.exists(legacyTest));
        assertTrue(Files.exists(integrationTest));

        String source = Files.readString(integrationTest);
        assertTrue(source.contains("TestDatabase.freshDatabase("));
        assertTrue(source.contains("System.getenv(\"CI\")"));
        assertFalse(source.contains("MIGRATION_TEST_DB_HOST"));
    }
}
