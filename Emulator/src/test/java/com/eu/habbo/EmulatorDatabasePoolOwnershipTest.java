package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EmulatorDatabasePoolOwnershipTest {

    @Test
    void startupDoesNotOverwriteDatabasePoolSizing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));
        int databaseConfigLoaded = source.indexOf("Emulator.config.loadFromDatabase();");
        int startupDefaults = source.indexOf("registerStartupConfigDefaults();");
        String runtimeInitialization = source.substring(databaseConfigLoaded, startupDefaults);

        assertFalse(runtimeInitialization.contains("setMaximumPoolSize"),
                "DatabasePool must remain the sole owner of db.pool.maxsize");
        assertFalse(runtimeInitialization.contains("setMinimumIdle"),
                "DatabasePool must remain the sole owner of db.pool.minsize");
    }
}
