package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationStartupContractTest {
    @Test
    void databaseRunsMigrationsBeforeReportingTheConnectionReady() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/database/Database.java"));

        assertOrdered(source,
                "this.runMigrations(migrationOptions)",
                "Database -> Connected!");
        assertTrue(source.contains("new DatabaseMigrationRunner"));
        assertTrue(source.contains("MigrationCatalog.load"));
        assertTrue(source.contains("migrationOptions.lockTimeoutSeconds()"));
        assertTrue(source.contains("SchemaContractLoader.load"));
        assertTrue(source.contains("new DatabaseSchemaValidator"));
        assertTrue(source.contains("this.dispose()"));
    }

    @Test
    void emulatorResolvesOptionsBeforeDatabaseAndExitsBeforeManagersInMigrationsOnlyMode()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/Emulator.java"));

        assertOrdered(source,
                "MigrationOptions.resolve",
                "new Database(");
        assertOrdered(source,
                "if (migrationOptions.migrationsOnly())",
                "new DatabaseLogger()");
        assertOrdered(source,
                "if (migrationOptions.migrationsOnly())",
                "new PluginManager()");
    }

    @Test
    void migrationToolRequiresMigrationsOnlyAndUsesTheNormalDatabaseGate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/database/migrations/MigrationTool.java"));

        assertTrue(source.contains("--migrations-only"));
        assertTrue(source.contains("MigrationOptions.resolve"));
        assertTrue(source.contains("MigrationMode.OFF"));
        assertTrue(source.contains("new Database(config, options)"));
        assertTrue(source.contains("database.dispose()"));
    }

    private static void assertOrdered(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "Missing source marker: " + first);
        assertTrue(secondIndex >= 0, () -> "Missing source marker: " + second);
        assertTrue(firstIndex < secondIndex,
                () -> "Expected '" + first + "' before '" + second + "'");
    }
}
