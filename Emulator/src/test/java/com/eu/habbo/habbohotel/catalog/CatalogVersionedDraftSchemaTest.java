package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogVersionedDraftSchemaTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260802090000__catalog_versioned_drafts.sql");
    private static final Path COMPATIBILITY_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260802100000__catalog_versioned_drafts_compatibility.sql");
    private static final Path SMART_SAVE_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260805210000__catalog_smart_save.sql");
    private static final Path OFFICIAL_IDENTITY_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260805213000__catalog_official_identity_utf8mb4.sql");

    @Test
    void migrationDefinesVersionRuntimeSnapshotJournalAndLocks() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_versions`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_runtime_state`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_version_pages`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_version_offers`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_id_sequences`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_change_groups`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_change_entries`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_edit_locks`")),
                () -> assertTrue(sql.contains("CREATE TABLE `catalog_operations`")),
                () -> assertTrue(
                        sql.contains("UNIQUE KEY `uq_catalog_version_page` (`version_id`,`catalog_type`,`page_id`)")),
                () -> assertTrue(
                        sql.contains("UNIQUE KEY `uq_catalog_version_offer` (`version_id`,`catalog_type`,`offer_id`)")),
                () -> assertTrue(sql.contains(
                        "UNIQUE KEY `uq_catalog_edit_lock` (`version_id`,`catalog_type`,`entity_type`,`entity_id`)")),
                () -> assertTrue(sql.contains("FROM `catalog_pages_bc`")),
                () -> assertTrue(sql.contains("FROM `catalog_items_bc`")));
    }

    @Test
    void draftOffersDoNotVersionOperationalLimitedSales() throws Exception {
        String sql = Files.readString(MIGRATION);
        String offersTable = sql.substring(
                sql.indexOf("CREATE TABLE `catalog_version_offers`"),
                sql.indexOf("CREATE TABLE `catalog_change_groups`"));

        assertTrue(!offersTable.contains("limited_sells"));
    }

    @Test
    void compatibilityMigrationUpgradesEarlyDraftSchemasWithoutDroppingCatalogData() throws Exception {
        String sql = Files.readString(COMPATIBILITY_MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS `catalog_type`")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `catalog_operations`")),
                () -> assertTrue(sql.contains(
                        "ADD UNIQUE KEY `uq_catalog_version_page` (`version_id`,`catalog_type`,`page_id`)")),
                () -> assertTrue(sql.contains(
                        "ADD UNIQUE KEY `uq_catalog_version_offer` (`version_id`,`catalog_type`,`offer_id`)")),
                () -> assertTrue(
                        sql.contains(
                                "ADD UNIQUE KEY `uq_catalog_edit_lock` (`version_id`,`catalog_type`,`entity_type`,`entity_id`)")),
                () -> assertTrue(sql.contains("FROM `catalog_pages_bc`")),
                () -> assertTrue(sql.contains("FROM `catalog_items_bc`")),
                () -> assertTrue(!sql.toUpperCase().contains("DROP TABLE")));
    }

    @Test
    void smartSaveMigrationAndRuntimeContractExposeReplayMetadata() throws Exception {
        String sql = Files.readString(SMART_SAVE_MIGRATION);
        String contract = Files.readString(Path.of("src/main/resources/db/runtime-schema-contract.json"));

        assertAll(
                () -> assertTrue(sql.contains("ADD COLUMN `request_fingerprint`")),
                () -> assertTrue(sql.contains("ADD COLUMN `history_group_id`")),
                () -> assertTrue(sql.contains("ADD COLUMN `result_json`")),
                () -> assertTrue(contract.contains("request_fingerprint")),
                () -> assertTrue(contract.contains("history_group_id")),
                () -> assertTrue(contract.contains("result_json")));
    }

    @Test
    void officialCatalogIdentityMigrationPreservesLongUnicodePageKeys() throws Exception {
        String sql = Files.readString(OFFICIAL_IDENTITY_MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("CONVERT TO CHARACTER SET utf8mb4")),
                () -> assertTrue(sql.contains("MODIFY COLUMN `caption_save` varchar(128)")),
                () -> assertTrue(sql.contains("ALTER TABLE `catalog_version_pages`")),
                () -> assertTrue(sql.contains("ALTER TABLE `catalog_items_bc`")));
    }
}
