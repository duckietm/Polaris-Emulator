package com.eu.habbo.database;

import com.eu.habbo.core.ConfigurationManager;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabasePoolSizingTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesConfiguredPoolSizes() throws Exception {
        Path configFile = tempDir.resolve("config.ini");
        Files.writeString(configFile, """
                db.pool.maxsize=37
                db.pool.minsize=7
                """);
        ConfigurationManager config = new ConfigurationManager(configFile.toString());
        HikariConfig hikariConfig = new HikariConfig();

        DatabasePool.applyPoolSizing(hikariConfig, config);

        assertEquals(37, hikariConfig.getMaximumPoolSize());
        assertEquals(7, hikariConfig.getMinimumIdle());
    }

    @Test
    void appliesDocumentedPoolDefaultsWhenSizesAreAbsent() throws Exception {
        Path configFile = tempDir.resolve("config.ini");
        Files.writeString(configFile, "");
        ConfigurationManager config = new ConfigurationManager(configFile.toString());
        HikariConfig hikariConfig = new HikariConfig();

        DatabasePool.applyPoolSizing(hikariConfig, config);

        assertEquals(50, hikariConfig.getMaximumPoolSize());
        assertEquals(10, hikariConfig.getMinimumIdle());
    }
}
