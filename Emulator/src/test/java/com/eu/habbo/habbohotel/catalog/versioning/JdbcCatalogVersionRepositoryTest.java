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
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcCatalogVersionRepositoryTest {

    @Test
    void locksAndLoadsTheSingletonRuntimeState() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOCK_RUNTIME_STATE_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        Instant updatedAt = Instant.parse("2026-08-02T09:30:00Z");
        when(resultSet.getLong("active_version_id")).thenReturn(7L);
        when(resultSet.getLong("draft_version_id")).thenReturn(8L);
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(updatedAt));

        CatalogRuntimeState state = new JdbcCatalogVersionRepository().lockRuntimeState(connection);

        assertEquals(7L, state.activeVersionId());
        assertEquals(8L, state.draftVersionId());
        assertEquals(updatedAt, state.updatedAt());
    }

    @Test
    void allocatesStableIdsWhileHoldingTheSequenceQueryLock() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement pageStatement = mock(PreparedStatement.class);
        PreparedStatement offerStatement = mock(PreparedStatement.class);
        PreparedStatement pageUpdate = mock(PreparedStatement.class);
        PreparedStatement offerUpdate = mock(PreparedStatement.class);
        ResultSet pageResult = mock(ResultSet.class);
        ResultSet offerResult = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'PAGE' "
                        + "AND catalog_type = ? FOR UPDATE"))
                .thenReturn(pageStatement);
        when(connection.prepareStatement("SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'OFFER' "
                        + "AND catalog_type = ? FOR UPDATE"))
                .thenReturn(offerStatement);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.UPDATE_NEXT_ID_SQL))
                .thenReturn(pageUpdate, offerUpdate);
        when(pageStatement.executeQuery()).thenReturn(pageResult);
        when(offerStatement.executeQuery()).thenReturn(offerResult);
        when(pageUpdate.executeUpdate()).thenReturn(1);
        when(offerUpdate.executeUpdate()).thenReturn(1);
        when(pageResult.next()).thenReturn(true);
        when(offerResult.next()).thenReturn(true);
        when(pageResult.getLong(1)).thenReturn(1121L);
        when(offerResult.getLong(1)).thenReturn(20541L);

        JdbcCatalogVersionRepository repository = new JdbcCatalogVersionRepository();

        assertEquals(1121L, repository.nextPageId(connection));
        assertEquals(20541L, repository.nextOfferId(connection));
        verify(pageUpdate).setLong(1, 1122L);
        verify(pageUpdate).setString(2, "PAGE");
        verify(pageUpdate).setString(3, "NORMAL");
        verify(offerUpdate).setLong(1, 20542L);
        verify(offerUpdate).setString(2, "OFFER");
        verify(offerUpdate).setString(3, "NORMAL");
    }

    @Test
    void allocatesBuildersClubIdsFromAnIndependentSequence() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement select = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'PAGE' "
                        + "AND catalog_type = ? FOR UPDATE"))
                .thenReturn(select);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.UPDATE_NEXT_ID_SQL))
                .thenReturn(update);
        when(select.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(3L);
        when(update.executeUpdate()).thenReturn(1);

        long pageId = new JdbcCatalogVersionRepository().nextPageId(connection, CatalogPageType.BUILDER);

        assertEquals(3L, pageId);
        verify(select).setString(1, "BUILDER");
        verify(update).setLong(1, 4L);
        verify(update).setString(2, "PAGE");
        verify(update).setString(3, "BUILDER");
    }

    @Test
    void incrementsOnlyTheExpectedDraftRevision() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.INCREMENT_REVISION_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        long revision = new JdbcCatalogVersionRepository().incrementRevision(connection, 8, 12);

        assertEquals(13L, revision);
        verify(statement).setLong(1, 8);
        verify(statement).setLong(2, 12);
    }

    @Test
    void rejectsAStaleDraftRevision() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.INCREMENT_REVISION_SQL))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(
                CatalogConcurrentModificationException.class,
                () -> new JdbcCatalogVersionRepository().incrementRevision(connection, 8, 12));
    }
}
