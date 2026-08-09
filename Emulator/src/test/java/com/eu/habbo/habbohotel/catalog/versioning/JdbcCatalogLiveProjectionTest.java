package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcCatalogLiveProjectionTest {

    @Test
    void offerUpsertPreservesTheOperationalLimitedSoldCounter() {
        String sql = JdbcCatalogLiveProjection.UPSERT_OFFER_SQL;

        assertTrue(sql.contains("limited_sells"));
        assertFalse(sql.contains("limited_sells = VALUES(limited_sells)"));
        assertFalse(sql.contains("limited_sells = 0"));
    }

    @Test
    void projectionDeletesOnlyRowsAbsentFromThePublishedVersion() {
        assertTrue(JdbcCatalogLiveProjection.DELETE_OFFERS_SQL.contains("catalog_version_offers"));
        assertTrue(JdbcCatalogLiveProjection.DELETE_OFFERS_SQL.contains("version_id = ?"));
        assertTrue(JdbcCatalogLiveProjection.DELETE_PAGES_SQL.contains("catalog_version_pages"));
        assertTrue(JdbcCatalogLiveProjection.DELETE_PAGES_SQL.contains("version_id = ?"));
    }

    @Test
    void projectionIncludesTheBuildersClubCompatibilityTables() {
        assertTrue(JdbcCatalogLiveProjection.UPSERT_BUILDER_PAGE_SQL.contains("catalog_pages_bc"));
        assertTrue(JdbcCatalogLiveProjection.UPSERT_BUILDER_OFFER_SQL.contains("catalog_items_bc"));
        assertTrue(JdbcCatalogLiveProjection.DELETE_BUILDER_PAGES_SQL.contains("catalog_type = 'BUILDER'"));
        assertTrue(JdbcCatalogLiveProjection.DELETE_BUILDER_OFFERS_SQL.contains("catalog_type = 'BUILDER'"));
    }

    @Test
    void projectionBindsLegacyEnumBooleansAsTheirStringValues() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        CatalogVersion version = new CatalogVersion(
                3, CatalogVersionStatus.PUBLISHED, 2L, 1, "Published", 1, Instant.EPOCH, 1, Instant.EPOCH);
        CatalogVersionSnapshot snapshot = new CatalogVersionSnapshot(
                version,
                List.of(page(CatalogPageType.NORMAL, 17, true, false), page(CatalogPageType.BUILDER, 18, false, true)),
                List.of(offer(true, false)));

        new JdbcCatalogLiveProjection().replace(connection, snapshot);

        verify(statement).setString(10, "1");
        verify(statement).setString(11, "0");
        verify(statement).setString(12, "0");
        verify(statement).setString(14, "0");
        verify(statement).setString(15, "1");
        verify(statement).setString(16, "0");
        verify(statement).setString(8, "0");
        verify(statement).setString(9, "1");
        verify(statement, never()).setBoolean(anyInt(), anyBoolean());
    }

    @Test
    void projectionSkipsOffersWhosePageIsAbsentFromThePublishedSnapshot() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement fallbackStatement = mock(PreparedStatement.class);
        PreparedStatement offerStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(fallbackStatement);
        when(connection.prepareStatement(JdbcCatalogLiveProjection.UPSERT_OFFER_SQL)).thenReturn(offerStatement);
        CatalogVersion version = new CatalogVersion(
                3, CatalogVersionStatus.PUBLISHED, 2L, 1, "Published", 1, Instant.EPOCH, 1, Instant.EPOCH);
        CatalogVersionSnapshot snapshot = new CatalogVersionSnapshot(
                version,
                List.of(page(CatalogPageType.NORMAL, 17, true, true)),
                List.of(offerAt(42, 17), offerAt(43, 999)));

        new JdbcCatalogLiveProjection().replace(connection, snapshot);

        verify(offerStatement, times(1)).addBatch();
    }

    private static CatalogPageSnapshot page(CatalogPageType catalogType, int pageId, boolean visible, boolean enabled) {
        return new CatalogPageSnapshot(
                catalogType,
                pageId,
                -1,
                "page_" + pageId,
                "Page " + pageId,
                "default_3x3",
                1,
                1,
                1,
                1,
                visible,
                enabled,
                false,
                catalogType.name(),
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }

    private static CatalogOfferSnapshot offer(boolean haveOffer, boolean clubOnly) {
        return new CatalogOfferSnapshot(42, "12", 17, "rare_dragon", 25, 0, 0, 1, 0, 1, -1, 0, "", haveOffer, clubOnly);
    }

    private static CatalogOfferSnapshot offerAt(int offerId, int pageId) {
        return new CatalogOfferSnapshot(
                offerId, "12", pageId, "rare_dragon", 25, 0, 0, 1, 0, 1, -1, 0, "", true, false);
    }
}
