package com.eu.habbo.habbohotel.soundboard;

import static com.eu.habbo.habbohotel.soundboard.SoundboardCatalogResult.Code.PERSISTENCE_FAILURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SoundboardCatalogRepositoryTest {

    @Test
    void auditFailureRollsBackTheCatalogMutation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement select = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement audit = mock(PreparedStatement.class);
        ResultSet current = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(select, update, audit);
        when(select.executeQuery()).thenReturn(current);
        when(current.next()).thenReturn(true);
        when(current.getInt("id")).thenReturn(7);
        when(current.getString("name")).thenReturn("Old bell");
        when(current.getString("url")).thenReturn("/old.mp3");
        when(current.getBoolean("enabled")).thenReturn(true);
        when(current.getInt("sort_order")).thenReturn(10);
        when(current.getInt("min_rank")).thenReturn(1);
        when(update.executeUpdate()).thenReturn(1);
        when(audit.executeUpdate()).thenThrow(new SQLException("audit unavailable"));

        SoundboardCatalogResult result = new SoundboardCatalogRepository(dataSource)
                .upsert(42, new SoundboardCatalogCommand(7, "New bell", "/new.mp3", 1, true));

        assertEquals(PERSISTENCE_FAILURE, result.code());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
