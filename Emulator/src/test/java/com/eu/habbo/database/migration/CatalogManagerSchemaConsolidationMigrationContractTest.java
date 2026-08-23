package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogManagerSchemaConsolidationMigrationContractTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260823120000__catalog_manager_schema_consolidation.sql");

    @Test
    void consolidatesManagerStateAuditIdempotencyAndUndoIntoTwoTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE `catalog_manager_state`"));
        assertTrue(sql.contains("CREATE TABLE `catalog_manager_history`"));
        assertTrue(sql.contains("`request_fingerprint` char(64)"));
        assertTrue(sql.contains("`changes_json` longtext NOT NULL"));
        assertTrue(sql.contains("JSON_ARRAYAGG"));
        assertTrue(sql.contains("DROP TABLE `catalog_operations`"));
        assertTrue(sql.contains("DROP TABLE `catalog_change_entries`"));
        assertTrue(sql.contains("DROP TABLE `catalog_change_groups`"));
        assertTrue(sql.contains("DROP TABLE `catalog_edit_locks`"));
        assertTrue(sql.contains("DROP TABLE `catalog_runtime_state`"));
        assertTrue(sql.contains("DROP TABLE `catalog_version_offers`"));
        assertTrue(sql.contains("DROP TABLE `catalog_version_pages`"));
        assertTrue(sql.contains("DROP TABLE `catalog_versions`"));
        assertFalse(sql.contains("FOREIGN_KEY_CHECKS"));
    }
}
