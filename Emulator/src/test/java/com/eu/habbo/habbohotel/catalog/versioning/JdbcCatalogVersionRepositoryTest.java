package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcCatalogVersionRepositoryTest {
    @Test
    void locksTheGlobalManagerRevision() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOCK_RUNTIME_STATE_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        Instant updatedAt = Instant.parse("2026-08-23T10:00:00Z");
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));

        CatalogRuntimeState state = new JdbcCatalogVersionRepository().lockRuntimeState(connection);

        assertEquals(1, state.activeVersionId());
        assertEquals(updatedAt, state.updatedAt());
    }

    @Test
    void incrementsOnlyTheExpectedGlobalRevision() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.INCREMENT_REVISION_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        assertEquals(13, new JdbcCatalogVersionRepository().incrementRevision(connection, 1, 12));
        verify(statement).setLong(1, 12);
    }

    @Test
    void rejectsAStaleGlobalRevision() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.INCREMENT_REVISION_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(
                CatalogConcurrentModificationException.class,
                () -> new JdbcCatalogVersionRepository().incrementRevision(connection, 1, 12));
    }

    @Test
    void rejectsLegacyBooleanOrdinals() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getInt("club_only")).thenReturn(2);

        assertThrows(SQLException.class, () -> JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "club_only"));
    }
}
