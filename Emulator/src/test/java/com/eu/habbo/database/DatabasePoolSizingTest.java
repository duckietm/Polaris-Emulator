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
    Path tempDirectory;

    @Test
    void appliesConfiguredPoolSizes() throws Exception {
        Path configFile = this.tempDirectory.resolve("config.ini");
        Files.writeString(configFile, """
                db.pool.maxsize=37
                db.pool.minsize=7
                """);
        ConfigurationManager configuration =
                new ConfigurationManager(configFile.toString());
        HikariConfig hikari = new HikariConfig();

        DatabasePool.applyPoolSizing(hikari, configuration);

        assertEquals(37, hikari.getMaximumPoolSize());
        assertEquals(7, hikari.getMinimumIdle());
    }

    @Test
    void appliesDocumentedDefaultsWhenSizesAreAbsent()
            throws Exception {
        Path configFile = this.tempDirectory.resolve("config.ini");
        Files.writeString(configFile, "");
        ConfigurationManager configuration =
                new ConfigurationManager(configFile.toString());
        HikariConfig hikari = new HikariConfig();

        DatabasePool.applyPoolSizing(hikari, configuration);

        assertEquals(50, hikari.getMaximumPoolSize());
        assertEquals(10, hikari.getMinimumIdle());
    }
}
