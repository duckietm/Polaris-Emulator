package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogSqlImportExportServiceTest {
    @Test
    void tokenizerKeepsSemicolonsAndCommentMarkersInsideQuotedStrings() {
        List<String> statements = new CatalogSqlTokenizer()
                .statements("UPDATE catalog_pages SET caption='A; -- still text' WHERE id=1;"
                        + "DELETE FROM catalog_items WHERE id=2;");

        assertEquals(2, statements.size());
        assertTrue(statements.getFirst().contains("A; -- still text"));
    }

    @Test
    void acceptsOnlyRestrictedCatalogMutationsWithoutExecutingSql() {
        CatalogSqlImportService service = new CatalogSqlImportService();
        List<CatalogSqlStatement> parsed =
                service.parse("UPDATE catalog_items SET cost_credits=25, catalog_name='Rare' WHERE id=42;");

        assertEquals(CatalogSqlAction.UPDATE, parsed.getFirst().action());
        assertEquals("catalog_items", parsed.getFirst().table());
        assertEquals("25", parsed.getFirst().values().get("cost_credits"));

        assertThrows(IllegalArgumentException.class, () -> service.parse("DROP TABLE catalog_items;"));
        assertThrows(IllegalArgumentException.class, () -> service.parse("UPDATE users SET rank=9 WHERE id=1;"));
        assertThrows(IllegalArgumentException.class, () -> service.parse("/*!50000 DROP TABLE catalog_items */;"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.parse("UPDATE catalog_items SET cost_credits=(SELECT 1) WHERE id=42;"));
    }

    @Test
    void dangerousKeywordsAndCommentMarkersRemainPlainTextInsideLiterals() {
        CatalogSqlImportService service = new CatalogSqlImportService();

        List<CatalogSqlStatement> parsed =
                service.parse("UPDATE catalog_pages SET caption='SELECT -- /* ! */ ;' WHERE id=17;");

        assertEquals("SELECT -- /* ! */ ;", parsed.getFirst().values().get("caption"));
    }

    @Test
    void updateAndDeleteCanTargetBuildersClubIdsWithoutTouchingNormalRows() {
        CatalogVersionSnapshot base = CatalogJsoncImportExportServiceTestSupport.snapshot();
        CatalogPageSnapshot builderPage = new CatalogPageSnapshot(
                com.eu.habbo.habbohotel.catalog.CatalogPageType.BUILDER,
                17,
                -1,
                "builder",
                "Builder",
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
                true,
                false,
                "BUILDERS_CLUB",
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
        CatalogVersionSnapshot both = new CatalogVersionSnapshot(
                base.version(), List.of(base.pages().getFirst(), builderPage), base.offers());

        CatalogImportDryRun updated = new CatalogSqlImportService()
                .dryRun(
                        both,
                        "UPDATE catalog_pages SET caption='Builder updated' "
                                + "WHERE id=17 AND catalog_type='BUILDER';");

        assertEquals(1, updated.changes().size());
        assertEquals(
                com.eu.habbo.habbohotel.catalog.CatalogPageType.BUILDER,
                updated.changes().getFirst().catalogType());
    }

    @Test
    void exportIsDeterministicAndNeverContainsOperationalLimitedSales() {
        CatalogVersionSnapshot snapshot = CatalogJsoncImportExportServiceTestSupport.snapshot();
        String sql = new CatalogSqlExportService().export(snapshot);

        assertTrue(sql.contains("INSERT INTO catalog_pages"));
        assertTrue(sql.contains("INSERT INTO catalog_items"));
        assertFalse(sql.contains("limited_sells"));
        assertTrue(sql.indexOf("catalog_pages") < sql.indexOf("catalog_items"));
        assertEquals(
                0, new CatalogSqlImportService().dryRun(snapshot, sql).changes().size());
    }
}
