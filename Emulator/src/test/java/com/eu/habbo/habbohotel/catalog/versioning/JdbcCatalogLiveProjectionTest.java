package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
