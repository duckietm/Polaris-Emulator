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
import org.junit.jupiter.api.Test;

class JdbcCatalogOperationRepositoryTest {

    private final JdbcCatalogOperationRepository repository = new JdbcCatalogOperationRepository();

    @Test
    void findForUpdateLoadsFingerprintAndOriginalResult() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogOperationRepository.SELECT_FOR_UPDATE))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(17L);
        when(resultSet.getString("source")).thenReturn("UI");
        when(resultSet.getLong("revision")).thenReturn(8L);
        when(resultSet.getString("request_fingerprint")).thenReturn("abc123");

        CatalogOperationRecord record =
                repository.findForUpdate(connection, "save-page-1", 9).orElseThrow();

        assertEquals("abc123", record.requestFingerprint());
        assertEquals(8, record.resultRevision());
        assertEquals(17L, record.historyGroupId());
        verify(statement).setString(1, "save-page-1");
        verify(statement).setInt(2, 9);
    }

    @Test
    void insertAttachesIdempotencyToTheHistoryRow() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogOperationRepository.ATTACH_TO_GROUP))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        repository.insert(connection, operationRecord());

        verify(statement).setString(1, "save-page-1");
        verify(statement).setString(2, "abc123");
        verify(statement).setLong(3, 17L);
        verify(statement).setInt(4, 9);
        verify(statement).executeUpdate();
    }

    @Test
    void findForUpdateRejectsAnOperationIdLongerThanTheDatabaseLimit() {
        Connection connection = mock(Connection.class);

        assertThrows(IllegalArgumentException.class, () -> repository.findForUpdate(connection, "x".repeat(97), 9));
    }

    @Test
    void insertRejectsAnyUpdateCountOtherThanOne() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogOperationRepository.ATTACH_TO_GROUP))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(SQLException.class, () -> repository.insert(connection, operationRecord()));
    }

    private static CatalogOperationRecord operationRecord() {
        return new CatalogOperationRecord(
                "save-page-1", 9, 1L, CatalogChangeSource.UI, 8L, "abc123", null, null, null, null, 17L, null);
    }
}
