package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * A reload cleared the live Properties and refilled them while other threads read, and a non-volatile
 * isLoading made those threads answer the defaults meanwhile; a runtime change to a key with no row in
 * the database was dropped by the save, silently.
 */
class ConfigurationReloadAtomicTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readersNeverSeeTheDefaultsWhileAReloadRuns() throws Exception {
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "hotel.name=Polaris\n");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger misses = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!done.get()) {
                if (!"Polaris".equals(manager.getValue("hotel.name", "default"))) {
                    misses.incrementAndGet();
                }
            }
        });
        reader.start();
        try {
            for (int i = 0; i < 300; i++) {
                manager.reload();
            }
        } finally {
            done.set(true);
            reader.join();
        }

        assertEquals(0, misses.get());
    }

    @Test
    void aSavedKeyWithoutARowIsInsertedNotLost() throws Exception {
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "");
        ConfigurationManager manager = new ConfigurationManager(config.toString());
        manager.update("hotel.new.setting", "1");
        manager.update("wired.new.setting", "2");

        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        Field databaseField = Emulator.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        Database original = (Database) databaseField.get(null);
        databaseField.set(null, databaseUsing(dataSource));

        try {
            manager.saveToDatabase();
        } finally {
            databaseField.set(null, original);
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().stream()
                .allMatch(q -> q.startsWith("INSERT INTO ")
                        && q.endsWith("ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)")));
        assertTrue(sql.getAllValues().stream()
                .anyMatch(q -> q.startsWith("INSERT INTO wired_emulator_settings (`value`, `key`, `comment`)")));
    }

    private static Database databaseUsing(HikariDataSource dataSource) throws Exception {
        Constructor<Database> constructor = Database.class.getDeclaredConstructor(HikariDataSource.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource);
    }
}
