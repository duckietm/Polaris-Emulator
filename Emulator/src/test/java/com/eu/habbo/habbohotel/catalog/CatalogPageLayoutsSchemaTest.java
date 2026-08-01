package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogPageLayoutsSchemaTest {

    @Test
    void migrationContainsEverySupportedCatalogPageLayout() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20260801110000__catalog_page_layout_alignment.sql"));

        for (CatalogPageLayouts layout : CatalogPageLayouts.values()) {
            assertTrue(
                    migration.contains("'" + layout.name() + "'"),
                    () -> "Missing database enum value for " + layout.name());
        }
    }
}
