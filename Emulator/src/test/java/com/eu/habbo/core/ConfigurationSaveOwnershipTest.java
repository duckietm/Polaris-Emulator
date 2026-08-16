package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationSaveOwnershipTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shutdownPersistsOnlyKeysChangedThroughTheRuntimeApi() throws Exception {
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "first=value\nsecond=database-owned\n");
        ConfigurationManager manager = new ConfigurationManager(config.toString());
        manager.update("first", "changed");

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

        verify(statement, times(1)).executeUpdate();
        verify(statement).setString(1, "changed");
        verify(statement).setString(2, "first");
    }

    @Test
    void staleDatabaseDefaultsCannotOverrideStartupOwnedProfiles() throws Exception {
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "runtime.operational.profile=small\n");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT * FROM emulator_settings")).thenReturn(true);
        when(statement.getResultSet()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("key")).thenReturn("runtime.operational.profile");
        when(result.getString("value")).thenReturn("custom");

        Field databaseField = Emulator.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        Database original = (Database) databaseField.get(null);
        databaseField.set(null, databaseUsing(dataSource));

        try {
            manager.loadFromDatabase();
        } finally {
            databaseField.set(null, original);
        }

        assertEquals("small", manager.getValue("runtime.operational.profile"));
    }

    private static Database databaseUsing(HikariDataSource dataSource) throws Exception {
        Constructor<Database> constructor = Database.class.getDeclaredConstructor(HikariDataSource.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource);
    }
}
