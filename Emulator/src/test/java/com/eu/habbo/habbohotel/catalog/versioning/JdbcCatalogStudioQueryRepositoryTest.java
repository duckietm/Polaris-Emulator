package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCatalogStudioQueryRepositoryTest {

    @Test
    void loadsACompleteSharedSessionFromServerState() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        PreparedStatement sessionStatement = statementWith(sessionRow());
        PreparedStatement pendingStatement = statementWith(pendingRow());
        PreparedStatement actorsStatement = statementWith(actorRow());
        PreparedStatement versionsStatement = statementWith(versionRow());
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_SESSION_SQL))
                .thenReturn(sessionStatement);
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_PENDING_COUNT_SQL))
                .thenReturn(pendingStatement);
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_ACTORS_SQL))
                .thenReturn(actorsStatement);
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_PUBLISHED_VERSIONS_SQL))
                .thenReturn(versionsStatement);

        CatalogStudioSessionState state = new JdbcCatalogStudioQueryRepository(dataSource).loadSession();

        assertEquals(11, state.activeVersionId());
        assertEquals(12, state.draftVersionId());
        assertEquals(7, state.revision());
        assertEquals(3, state.pendingCount());
        assertEquals("Alice", state.actors().getFirst().username());
        assertEquals("Summer catalog", state.publishedVersions().getFirst().label());
        assertTrue(state.publishedVersions().getFirst().publishedAt().equals(Instant.parse("2026-08-02T10:00:00Z")));
    }

    private static PreparedStatement statementWith(ResultSet resultSet) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        return statement;
    }

    private static ResultSet sessionRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getLong("active_version_id")).thenReturn(11L);
        when(result.getLong("draft_version_id")).thenReturn(12L);
        when(result.getLong("revision")).thenReturn(7L);
        when(result.getTimestamp("active_updated_at"))
                .thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:00:00Z")));
        when(result.getTimestamp("draft_created_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:05:00Z")));
        return result;
    }

    private static ResultSet pendingRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getInt("pending_count")).thenReturn(3);
        return result;
    }

    private static ResultSet actorRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getInt("owner_id")).thenReturn(9);
        when(result.getString("username")).thenReturn("Alice");
        return result;
    }

    private static ResultSet versionRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getLong("id")).thenReturn(11L);
        when(result.getString("label")).thenReturn("Summer catalog");
        when(result.getTimestamp("published_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:00:00Z")));
        return result;
    }

    private static ResultSet oneRow() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        return result;
    }
}
