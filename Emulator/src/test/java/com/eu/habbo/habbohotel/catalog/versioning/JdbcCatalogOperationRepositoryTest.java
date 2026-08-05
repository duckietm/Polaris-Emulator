package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class JdbcCatalogOperationRepositoryTest {

    private static final String SELECT_FOR_UPDATE = "SELECT operation_id, actor_id, version_id, source, result_revision, "
            + "request_fingerprint, action, entity_type, catalog_type, entity_id, history_group_id, result_json "
            + "FROM catalog_operations WHERE operation_id = ? AND actor_id = ? FOR UPDATE";
    private static final String INSERT = "INSERT INTO catalog_operations "
            + "(operation_id, actor_id, version_id, source, result_revision, request_fingerprint, action, entity_type, "
            + "catalog_type, entity_id, history_group_id, result_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcCatalogOperationRepository repository = new JdbcCatalogOperationRepository();

    @Test
    void findForUpdateLoadsFingerprintAndOriginalResult() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(SELECT_FOR_UPDATE)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("operation_id")).thenReturn("save-page-1");
        when(resultSet.getInt("actor_id")).thenReturn(9);
        when(resultSet.getLong("version_id")).thenReturn(4L);
        when(resultSet.getString("source")).thenReturn("UI");
        when(resultSet.getLong("result_revision")).thenReturn(8L);
        when(resultSet.getString("request_fingerprint")).thenReturn("abc123");
        when(resultSet.getString("action")).thenReturn("UPDATE");
        when(resultSet.getString("entity_type")).thenReturn("PAGE");
        when(resultSet.getString("catalog_type")).thenReturn("NORMAL");
        when(resultSet.getObject("entity_id", Integer.class)).thenReturn(42);
        when(resultSet.getObject("history_group_id", Long.class)).thenReturn(17L);
        when(resultSet.getString("result_json")).thenReturn("{\"revision\":8}");

        CatalogOperationRecord record = repository.findForUpdate(connection, "save-page-1", 9).orElseThrow();

        assertEquals("abc123", record.requestFingerprint());
        assertEquals("{\"revision\":8}", record.resultJson());
        assertEquals(CatalogChangeOperation.UPDATE, record.action());
        assertEquals(17L, record.historyGroupId());
        verify(statement).setString(1, "save-page-1");
        verify(statement).setInt(2, 9);
    }

    @Test
    void insertPersistsEverySmartSaveIdentityField() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(INSERT)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        repository.insert(connection, operationRecord());

        verify(statement).setString(1, "save-page-1");
        verify(statement).setInt(2, 9);
        verify(statement).setLong(3, 4L);
        verify(statement).setString(4, "UI");
        verify(statement).setLong(5, 8L);
        verify(statement).setString(6, "abc123");
        verify(statement).setString(7, "UPDATE");
        verify(statement).setString(8, "PAGE");
        verify(statement).setString(9, "NORMAL");
        verify(statement).setInt(10, 42);
        verify(statement).setLong(11, 17L);
        verify(statement).setString(12, "{\"revision\":8}");
        verify(statement).executeUpdate();
    }

    @Test
    void findForUpdateRejectsAnOperationIdLongerThanTheDatabaseLimit() {
        Connection connection = mock(Connection.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findForUpdate(connection, "x".repeat(97), 9));
    }

    @Test
    void insertRejectsAnyUpdateCountOtherThanOne() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(INSERT)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(SQLException.class, () -> repository.insert(connection, operationRecord()));
    }

    private static CatalogOperationRecord operationRecord() {
        return new CatalogOperationRecord(
                "save-page-1",
                9,
                4L,
                CatalogChangeSource.UI,
                8L,
                "abc123",
                CatalogChangeOperation.UPDATE,
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                42,
                17L,
                "{\"revision\":8}");
    }
}
