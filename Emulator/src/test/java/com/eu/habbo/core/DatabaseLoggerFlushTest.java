package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The chat, command and error logs are written in batches. save() drains its queue
 * before writing, so a database that cannot be reached used to take the whole batch
 * with it - the entries were already off the queue and the snapshot went out of scope.
 */
class DatabaseLoggerFlushTest {

    /** Counts how many times it was asked to write itself. */
    private static final class CountingLoggable implements DatabaseLoggable {
        private int written;

        @Override
        public String getQuery() {
            return "INSERT INTO chatlogs_private (message) VALUES (?)";
        }

        @Override
        public void log(PreparedStatement statement) {
            this.written++;
        }
    }

    private static final class FlakyDataSource extends HikariDataSource {
        private boolean broken;

        @Override
        public Connection getConnection() throws SQLException {
            if (this.broken) {
                throw new SQLException("no connection");
            }

            Connection connection = mock(Connection.class);
            when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
            return connection;
        }
    }

    private FlakyDataSource dataSource;
    private Field databaseField;
    private Database previousDatabase;

    @BeforeEach
    void installDatabase() throws Exception {
        this.dataSource = new FlakyDataSource();
        Constructor<Database> constructor = Database.class.getDeclaredConstructor(HikariDataSource.class);
        constructor.setAccessible(true);

        this.databaseField = Emulator.class.getDeclaredField("database");
        this.databaseField.setAccessible(true);
        this.previousDatabase = (Database) this.databaseField.get(null);
        this.databaseField.set(null, constructor.newInstance(this.dataSource));
    }

    @AfterEach
    void restoreDatabase() throws Exception {
        this.databaseField.set(null, this.previousDatabase);
    }

    @Test
    void aBatchThatCouldNotBeWrittenWaitsForTheNextFlush() {
        DatabaseLogger logger = new DatabaseLogger();
        CountingLoggable entry = new CountingLoggable();
        logger.store(entry);

        this.dataSource.broken = true;
        logger.save();
        assertEquals(0, entry.written, "nothing is written while the database is unreachable");

        this.dataSource.broken = false;
        logger.save();
        assertEquals(1, entry.written, "the entry was kept and written on the next flush");
    }

    @Test
    void aFlushedBatchIsNotWrittenTwice() {
        DatabaseLogger logger = new DatabaseLogger();
        CountingLoggable entry = new CountingLoggable();
        logger.store(entry);

        logger.save();
        logger.save();

        assertEquals(1, entry.written);
    }
}
