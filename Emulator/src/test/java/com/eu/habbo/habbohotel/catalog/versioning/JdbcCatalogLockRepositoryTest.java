package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCatalogLockRepositoryTest {

    @Test
    void findsAnActiveLockInsideTheCallersTransaction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        String sql = "SELECT owner_id, token, expires_at FROM catalog_edit_locks "
                + "WHERE version_id = ? AND catalog_type = ? AND entity_type = ? AND entity_id = ? FOR UPDATE";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        UUID token = UUID.fromString("8b353470-27dd-4f7f-87d6-7349e11dc514");
        when(resultSet.getInt("owner_id")).thenReturn(7);
        when(resultSet.getString("token")).thenReturn(token.toString());
        when(resultSet.getTimestamp("expires_at")).thenReturn(Timestamp.from(Instant.parse("2026-08-02T12:00:00Z")));
        CatalogLockKey key = new CatalogLockKey(CatalogEntityType.PAGE, CatalogPageType.BUILDER, 17);

        Optional<CatalogLockRecord> lock = new JdbcCatalogLockRepository(dataSource)
                .findActive(connection, 2, key, Instant.parse("2026-08-02T11:00:00Z"));

        assertTrue(lock.isPresent());
        assertEquals(7, lock.orElseThrow().ownerId());
        assertEquals(token, lock.orElseThrow().token());
        org.mockito.Mockito.verify(statement).setString(2, "BUILDER");
        org.mockito.Mockito.verify(statement).setString(3, "PAGE");
        org.mockito.Mockito.verify(statement).setInt(4, 17);
    }
}
