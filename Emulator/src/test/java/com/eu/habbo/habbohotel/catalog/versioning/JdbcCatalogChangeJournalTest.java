package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcCatalogChangeJournalTest {
    @Test
    void loadsCompleteUndoDataFromOneHistoryRow() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.LOAD_GROUP_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(12L);
        when(resultSet.getLong("revision")).thenReturn(4L);
        when(resultSet.getInt("actor_id")).thenReturn(7);
        when(resultSet.getString("summary")).thenReturn("Edit page");
        when(resultSet.getString("source")).thenReturn("UI");
        when(resultSet.getString("changes_json")).thenReturn("""
                [{"id":22,"entityType":"PAGE","catalogType":"NORMAL","entityId":17,
                  "operation":"UPDATE","beforeJson":"{}","afterJson":"{}"}]
                """);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:00:00Z")));

        CatalogChangeGroup group = new JdbcCatalogChangeJournal().load(connection, 12);

        assertEquals(1, group.versionId());
        assertEquals(17, group.entries().getFirst().entityId());
        assertEquals(CatalogPageType.NORMAL, group.entries().getFirst().catalogType());
    }

    @Test
    void appendsOneSelfContainedJsonHistoryRow() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        when(statement.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(31L);
        CatalogChangeEntry entry = new CatalogChangeEntry(
                0, CatalogEntityType.PAGE, CatalogPageType.NORMAL, 17, CatalogChangeOperation.UPDATE, "{}", "{}");

        long historyId = new JdbcCatalogChangeJournal()
                .append(connection, 1, 5, 9, "Edit page", CatalogChangeSource.UI, List.of(entry));

        assertEquals(31, historyId);
        verify(statement).setLong(1, 5);
        verify(statement).setString(4, "UI");
        verify(statement).setString(org.mockito.ArgumentMatchers.eq(5), anyString());
    }

    @Test
    void detectsConflictsByInspectingLaterOperationJson() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.LATER_CONFLICT_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("changes_json")).thenReturn("""
                [{"id":0,"entityType":"PAGE","catalogType":"NORMAL","entityId":17,
                  "operation":"UPDATE","beforeJson":"{}","afterJson":"{}"}]
                """);
        CatalogChangeGroup group = new CatalogChangeGroup(
                12,
                1,
                4,
                7,
                "Edit page",
                CatalogChangeSource.UI,
                Instant.parse("2026-08-02T10:00:00Z"),
                List.of(new CatalogChangeEntry(
                        0,
                        CatalogEntityType.PAGE,
                        CatalogPageType.NORMAL,
                        17,
                        CatalogChangeOperation.UPDATE,
                        "{}",
                        "{}")));

        assertTrue(new JdbcCatalogChangeJournal().hasLaterChangesToSameEntities(connection, group));
        verify(statement).setLong(1, 4);
    }
}
