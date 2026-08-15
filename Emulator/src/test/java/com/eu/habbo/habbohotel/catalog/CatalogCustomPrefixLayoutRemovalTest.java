package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogCustomPrefixLayoutRemovalTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260814210000__remove_catalog_custom_prefix_layout.sql");

    @Test
    void catalogLayoutKeepsItsPluginAbiWithoutBeingRuntimeSelectable() {
        assertEquals(CatalogPageLayouts.custom_prefix, CatalogPageLayouts.valueOf("custom_prefix"));
        assertFalse(CatalogManager.pageDefinitions.containsKey("custom_prefix"));
    }

    @Test
    void migrationConvertsExistingPagesBeforeNarrowingBothEnums() throws Exception {
        String sql = Files.readString(MIGRATION);
        int normalUpdate = sql.indexOf("UPDATE `catalog_pages`");
        int buildersUpdate = sql.indexOf("UPDATE `catalog_pages_bc`");
        int firstAlter = sql.indexOf("ALTER TABLE");

        assertAll(
                () -> assertTrue(normalUpdate >= 0 && normalUpdate < firstAlter),
                () -> assertTrue(buildersUpdate >= 0 && buildersUpdate < firstAlter),
                () -> assertTrue(sql.contains("WHERE `page_layout` = 'custom_prefix'")),
                () -> assertTrue(!sql.substring(firstAlter).contains("'custom_prefix'")));
    }
}
