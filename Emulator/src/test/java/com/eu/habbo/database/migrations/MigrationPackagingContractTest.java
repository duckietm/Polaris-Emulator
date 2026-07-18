package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPackagingContractTest {
    @Test
    void mavenPackagesOnlyTopLevelDatabaseUpdatesAsMigrationResources() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("../Database/Database Updates"));
        assertTrue(pom.contains("<targetPath>db/migrations</targetPath>"));
        assertTrue(pom.contains("<include>*.sql</include>"));
        assertFalse(pom.contains("Own_Database_RunFirst/**"));
    }
}
