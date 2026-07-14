package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationDocumentationContractTest {
    @Test
    void configurationDocumentsSafeMigrationDefaults() throws Exception {
        String config = read("config example/config.ini.example");

        assertTrue(config.contains("db.migrations.mode=validate"));
        assertTrue(config.contains("db.migrations.lock_timeout_seconds=10"));
        assertTrue(config.contains("validate (default), apply, or off"));
    }

    @Test
    void operatorGuideCoversModesHistoryAndRecovery() throws Exception {
        String guide = read("e2e/README.md");

        for (String required : new String[]{
                "DB_MIGRATIONS_MODE",
                "--migrations=apply",
                "--migrations-only",
                "schema_migrations",
                "027",
                "GET_LOCK",
                "FullDatabase.sql",
                "checksum",
                "off"
        }) {
            assertTrue(guide.contains(required), () -> "Missing operator documentation: " + required);
        }
        assertTrue(guide.contains("never applies pending SQL"));
        assertTrue(guide.contains("must not be edited"));
    }

    @Test
    void authorGuideDefinesTheImmutableContiguousMigrationContract() throws Exception {
        String guide = read("Database/Database Updates/README.md");

        for (String required : new String[]{
                "NNN_lowercase_description.sql",
                "028",
                "contiguous",
                "retry-safe",
                "DELIMITER",
                "checksum",
                "FullDatabase.sql",
                "existing database",
                "must not be edited"
        }) {
            assertTrue(guide.contains(required), () -> "Missing author documentation: " + required);
        }
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of("..").resolve(relativePath));
    }
}
