package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void loadsOneVersionWithoutLoadingTheWholeSnapshot() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Instant createdAt = Instant.parse("2026-08-05T10:00:00Z");
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOAD_VERSION_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(12L);
        when(resultSet.getString("status")).thenReturn("DRAFT");
        when(resultSet.getLong("based_on_version_id")).thenReturn(7L);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("revision")).thenReturn(3L);
        when(resultSet.getString("label")).thenReturn("Current draft");
        when(resultSet.getInt("created_by")).thenReturn(4);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(resultSet.getInt("published_by")).thenReturn(0);
        when(resultSet.getTimestamp("published_at")).thenReturn(null);

        CatalogVersion version = new JdbcCatalogVersionRepository().loadVersion(connection, 12);

        assertEquals(12L, version.id());
        assertEquals(CatalogVersionStatus.DRAFT, version.status());
        verify(statement).setLong(1, 12L);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_PAGES_SQL);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_OFFERS_SQL);
    }

    @Test
    void loadsOnePageUsingVersionTypeAndPageId() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOAD_PAGE_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        stubPageRow(resultSet);

        CatalogPageSnapshot page = new JdbcCatalogVersionRepository()
                .loadPage(connection, 12, CatalogPageType.NORMAL, 42)
                .orElseThrow();

        assertEquals(42, page.pageId());
        verify(statement).setLong(1, 12L);
        verify(statement).setString(2, "NORMAL");
        verify(statement).setInt(3, 42);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_PAGES_SQL);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_OFFERS_SQL);
    }

    @Test
    void returnsEmptyWhenTheTargetedOfferIsMissing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(JdbcCatalogVersionRepository.LOAD_OFFER_SQL))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertTrue(new JdbcCatalogVersionRepository()
                .loadOffer(connection, 12, CatalogPageType.BUILDER, 99)
                .isEmpty());

        verify(statement).setLong(1, 12L);
        verify(statement).setString(2, "BUILDER");
        verify(statement).setInt(3, 99);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_PAGES_SQL);
        verify(connection, never()).prepareStatement(JdbcCatalogVersionRepository.LOAD_OFFERS_SQL);
    }

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

    private static void stubPageRow(ResultSet resultSet) throws Exception {
        when(resultSet.getString("catalog_type")).thenReturn("NORMAL");
        when(resultSet.getInt("page_id")).thenReturn(42);
        when(resultSet.getInt("parent_id")).thenReturn(-1);
        when(resultSet.getString("caption_save")).thenReturn("root");
        when(resultSet.getString("caption")).thenReturn("Root");
        when(resultSet.getString("page_layout")).thenReturn("default_3x3");
        when(resultSet.getInt("icon_color")).thenReturn(1);
        when(resultSet.getInt("icon_image")).thenReturn(2);
        when(resultSet.getInt("min_rank")).thenReturn(1);
        when(resultSet.getInt("order_num")).thenReturn(3);
        when(resultSet.getBoolean("visible")).thenReturn(true);
        when(resultSet.getBoolean("enabled")).thenReturn(true);
        when(resultSet.getBoolean("club_only")).thenReturn(false);
        when(resultSet.getString("catalog_mode")).thenReturn("NORMAL");
        when(resultSet.getBoolean("vip_only")).thenReturn(false);
        when(resultSet.getString("page_headline")).thenReturn("");
        when(resultSet.getString("page_teaser")).thenReturn("");
        when(resultSet.getString("page_special")).thenReturn("");
        when(resultSet.getString("page_text1")).thenReturn("");
        when(resultSet.getString("page_text2")).thenReturn("");
        when(resultSet.getString("page_text_details")).thenReturn("");
        when(resultSet.getString("page_text_teaser")).thenReturn("");
        when(resultSet.getInt("room_id")).thenReturn(0);
        when(resultSet.getString("includes")).thenReturn("");
    }
}
