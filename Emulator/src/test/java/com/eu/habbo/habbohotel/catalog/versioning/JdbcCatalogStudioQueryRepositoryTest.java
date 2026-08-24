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
    void readsOnlyTheSingletonLiveManagerState() throws Exception {
        assertTrue(JdbcCatalogStudioQueryRepository.LOAD_SESSION_SQL.contains("catalog_manager_state"));

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_SESSION_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("revision")).thenReturn(17L);
        Instant updatedAt = Instant.parse("2026-08-23T10:00:00Z");
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));

        CatalogStudioSessionState state = new JdbcCatalogStudioQueryRepository(dataSource).loadSession();

        assertEquals(1, state.activeVersionId());
        assertEquals(1, state.draftVersionId());
        assertEquals(17, state.revision());
        assertEquals(updatedAt, state.activeUpdatedAt());
        assertEquals(0, state.pendingCount());
        assertTrue(state.actors().isEmpty());
        assertTrue(state.publishedVersions().isEmpty());
    }

    @Test
    void historyReadModelUsesTheConsolidatedJournal() {
        assertTrue(JdbcCatalogStudioQueryRepository.LOAD_HISTORY_META_SQL.contains("catalog_manager_history"));
        assertTrue(JdbcCatalogStudioQueryRepository.LOAD_HISTORY_GROUPS_SQL.contains("catalog_manager_history"));
    }
}
