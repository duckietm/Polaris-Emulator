package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcCatalogChangeJournalTest {

    @Test
    void loadsAChangeGroupWithItsOrderedEntries() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement groupStatement = mock(PreparedStatement.class);
        PreparedStatement entryStatement = mock(PreparedStatement.class);
        ResultSet groupResult = mock(ResultSet.class);
        ResultSet entryResult = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.LOAD_GROUP_SQL))
                .thenReturn(groupStatement);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.LOAD_ENTRIES_SQL))
                .thenReturn(entryStatement);
        when(groupStatement.executeQuery()).thenReturn(groupResult);
        when(entryStatement.executeQuery()).thenReturn(entryResult);
        when(groupResult.next()).thenReturn(true);
        when(groupResult.getLong("id")).thenReturn(12L);
        when(groupResult.getLong("version_id")).thenReturn(2L);
        when(groupResult.getLong("revision")).thenReturn(4L);
        when(groupResult.getInt("actor_id")).thenReturn(7);
        when(groupResult.getString("summary")).thenReturn("Edit page");
        when(groupResult.getString("source")).thenReturn("UI");
        when(groupResult.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T10:00:00Z")));
        when(entryResult.next()).thenReturn(true, false);
        when(entryResult.getLong("id")).thenReturn(22L);
        when(entryResult.getString("entity_type")).thenReturn("PAGE");
        when(entryResult.getString("catalog_type")).thenReturn("NORMAL");
        when(entryResult.getInt("entity_id")).thenReturn(17);
        when(entryResult.getString("operation")).thenReturn("UPDATE");
        when(entryResult.getString("before_json")).thenReturn("{\"caption\":\"Old\"}");
        when(entryResult.getString("after_json")).thenReturn("{\"caption\":\"New\"}");

        CatalogChangeGroup group = new JdbcCatalogChangeJournal().load(connection, 12);

        assertEquals(12, group.id());
        assertEquals(CatalogChangeSource.UI, group.source());
        assertEquals(1, group.entries().size());
        assertEquals(CatalogEntityType.PAGE, group.entries().getFirst().entityType());
        assertEquals("{\"caption\":\"New\"}", group.entries().getFirst().afterJson());
    }

    @Test
    void appendsOneGroupAndAllEntriesUsingTheReturnedGroupId() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement groupStatement = mock(PreparedStatement.class);
        PreparedStatement entryStatement = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(groupStatement);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.INSERT_ENTRY_SQL))
                .thenReturn(entryStatement);
        when(groupStatement.executeUpdate()).thenReturn(1);
        when(groupStatement.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(31L);
        when(entryStatement.executeBatch()).thenReturn(new int[] {1});
        CatalogChangeEntry entry = new CatalogChangeEntry(
                0,
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                "{\"caption\":\"Old\"}",
                "{\"caption\":\"New\"}");

        long groupId = new JdbcCatalogChangeJournal()
                .append(connection, 2, 5, 9, "Undo change", CatalogChangeSource.UNDO, List.of(entry));

        assertEquals(31, groupId);
        verify(entryStatement).setLong(1, 31L);
        verify(entryStatement).setString(2, "PAGE");
        verify(entryStatement).setString(3, "NORMAL");
        verify(entryStatement).setInt(4, 17);
        verify(entryStatement).addBatch();
    }

    @Test
    void detectsALaterChangeToAnyEntityInTheSelectedGroup() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogChangeJournal.LATER_CONFLICT_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        CatalogChangeGroup group = new CatalogChangeGroup(
                12,
                2,
                4,
                7,
                "Edit page",
                CatalogChangeSource.UI,
                Instant.parse("2026-08-02T10:00:00Z"),
                List.of(new CatalogChangeEntry(
                        22, CatalogEntityType.PAGE, 17, CatalogChangeOperation.UPDATE, "{}", "{}")));

        boolean conflict = new JdbcCatalogChangeJournal().hasLaterChangesToSameEntities(connection, group);

        assertTrue(conflict);
        verify(statement).setLong(1, group.id());
        verify(statement).setLong(2, group.versionId());
        verify(statement).setLong(3, group.revision());
    }
}
