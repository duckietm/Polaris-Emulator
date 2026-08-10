package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCatalogStudioQueryRepositoryTest {
    @Test
    void listsCurrentAndPreviousPublishedVersionsForRestore() {
        assertTrue(JdbcCatalogStudioQueryRepository.LOAD_PUBLISHED_VERSIONS_SQL.contains("'PUBLISHED'"));
        assertTrue(JdbcCatalogStudioQueryRepository.LOAD_PUBLISHED_VERSIONS_SQL.contains("'ARCHIVED'"));
    }

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

    @Test
    void loadsDraftPageIndexWithoutReadingEveryOffer() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        PreparedStatement statusStatement = statementWith(draftVersionRow());
        PreparedStatement pagesStatement = statementWith(pageRow());
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_DRAFT_STATUS_SQL))
                .thenReturn(statusStatement);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOAD_PAGES_SQL))
                .thenReturn(pagesStatement);

        List<CatalogPageSnapshot> pages = new JdbcCatalogStudioQueryRepository(dataSource).loadDraftPages(12);

        assertEquals(1, pages.size());
        assertEquals(17, pages.getFirst().pageId());
        assertEquals("Front Page", pages.getFirst().caption());
        verify(connection, times(2)).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadsHistoryEntriesForAllSelectedGroupsInOneQuery() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        PreparedStatement metaStatement = statementWith(historyMetaRow());
        PreparedStatement groupsStatement = statementWith(historyGroupRows());
        PreparedStatement entriesStatement = statementWith(historyEntryRows());
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_HISTORY_META_SQL))
                .thenReturn(metaStatement);
        when(connection.prepareStatement(JdbcCatalogStudioQueryRepository.LOAD_HISTORY_GROUPS_SQL))
                .thenReturn(groupsStatement);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.startsWith(
                        JdbcCatalogStudioQueryRepository.LOAD_HISTORY_ENTRIES_BATCH_PREFIX)))
                .thenReturn(entriesStatement);

        CatalogHistoryPage history = new JdbcCatalogStudioQueryRepository(dataSource).loadHistory(12, 0, 3);

        assertEquals(3, history.groups().size());
        assertEquals(11, history.groups().get(0).id());
        assertEquals(101, history.groups().get(0).entries().getFirst().entityId());
        assertEquals(202, history.groups().get(1).entries().getFirst().entityId());
        assertEquals(303, history.groups().get(2).entries().getFirst().entityId());
        verify(connection, times(3)).prepareStatement(org.mockito.ArgumentMatchers.anyString());
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

    private static ResultSet draftVersionRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getString("status")).thenReturn("DRAFT");
        return result;
    }

    private static ResultSet pageRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getString("catalog_type")).thenReturn("NORMAL");
        when(result.getInt("page_id")).thenReturn(17);
        when(result.getInt("parent_id")).thenReturn(-1);
        when(result.getString("caption_save")).thenReturn("front_page");
        when(result.getString("caption")).thenReturn("Front Page");
        when(result.getString("page_layout")).thenReturn("default_3x3");
        when(result.getInt("min_rank")).thenReturn(1);
        return result;
    }

    private static ResultSet historyMetaRow() throws Exception {
        ResultSet result = oneRow();
        when(result.getLong("revision")).thenReturn(8L);
        when(result.getInt("total_count")).thenReturn(3);
        return result;
    }

    private static ResultSet historyGroupRows() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, true, true, false);
        when(result.getLong("id")).thenReturn(11L, 12L, 13L);
        when(result.getLong("revision")).thenReturn(8L, 7L, 6L);
        when(result.getInt("actor_id")).thenReturn(9);
        when(result.getString("actor_name")).thenReturn("Alice");
        when(result.getString("summary")).thenReturn("First", "Second", "Third");
        when(result.getString("source")).thenReturn("UI");
        when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:05:00Z")));
        return result;
    }

    private static ResultSet historyEntryRows() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, true, true, false);
        when(result.getLong("group_id")).thenReturn(11L, 12L, 13L);
        when(result.getString("entity_type")).thenReturn("PAGE", "OFFER", "PAGE");
        when(result.getInt("entity_id")).thenReturn(101, 202, 303);
        when(result.getString("operation")).thenReturn("UPDATE", "CREATE", "MOVE");
        return result;
    }

    private static ResultSet oneRow() throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true, false);
        return result;
    }
}
