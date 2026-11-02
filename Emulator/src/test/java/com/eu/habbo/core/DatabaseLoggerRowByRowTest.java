package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A group of log rows was written as one batch, and one bad row threw the whole group away, chat lines included.
 * The group is now written again one row at a time and only the rows that fail are lost.
 */
class DatabaseLoggerRowByRowTest {

    @Test
    void onlyTheRowThatFailsIsDropped() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        DatabaseLoggable good = mock(DatabaseLoggable.class);
        DatabaseLoggable bad = mock(DatabaseLoggable.class);
        doThrow(new SQLException("Data too long")).when(bad).log(statement);

        int dropped = DatabaseLogger.writeOneByOne(
                connection, "INSERT INTO chatlogs_room VALUES (?)", List.of(good, bad, good));

        assertEquals(1, dropped);
        verify(good, times(2)).log(statement);
        verify(statement, times(2)).executeBatch();
    }
}
