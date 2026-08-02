package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogJsoncImportExportServiceTest {
    @Test
    void exportsEnglishCommentsAndRoundTripsWithoutChanges() {
        CatalogVersionSnapshot snapshot = snapshot();
        String jsonc = new CatalogJsoncExportService().export(snapshot);

        CatalogJsoncDocument document = new CatalogJsoncImportService().parse(jsonc);

        assertTrue(jsonc.contains("// Catalog Studio JSONC export"));
        assertTrue(jsonc.contains("// Pages use stable IDs"));
        assertEquals(snapshot.version().id(), document.baseVersionId());
        assertEquals(snapshot.pages(), document.pages());
        assertEquals(snapshot.offers(), document.offers());
    }

    @Test
    void keepsCommentMarkersInsideStringsAndRejectsDuplicateKeys() {
        String valid = new CatalogJsoncExportService().export(snapshot()).replace("\"Page\"", "\"Page // still text\"");
        assertEquals(
                "Page // still text",
                new CatalogJsoncImportService().parse(valid).pages().getFirst().caption());

        String duplicate = "{\"schemaVersion\":1,\"schemaVersion\":1,\"baseVersionId\":1,"
                + "\"baseRevision\":0,\"pages\":[],\"offers\":[]}";
        assertThrows(IllegalArgumentException.class, () -> new CatalogJsoncImportService().parse(duplicate));
    }

    @Test
    void dryRunProducesOneFingerprintProtectedReversibleChange() {
        CatalogVersionSnapshot snapshot = snapshot();
        String edited = new CatalogJsoncExportService()
                .export(snapshot)
                .replace("\"caption\": \"Page\"", "\"caption\": \"Edited\"");

        CatalogImportDryRun dryRun = new CatalogJsoncImportService().dryRun(snapshot, edited);

        assertEquals(1, dryRun.changes().size());
        assertTrue(dryRun.fingerprint().matches("[0-9a-f]{64}"));
        assertEquals(CatalogChangeSource.JSONC, dryRun.source());
    }

    @Test
    void rejectsUnknownSchemasAndMissingPageReferences() {
        CatalogJsoncImportService service = new CatalogJsoncImportService();
        String exported = new CatalogJsoncExportService().export(snapshot());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.parse(exported.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        int offersStart = exported.indexOf("\"offers\"");
        String missingPage = exported.substring(0, offersStart)
                + exported.substring(offersStart).replace("\"pageId\": 17", "\"pageId\": 999");
        assertThrows(IllegalArgumentException.class, () -> service.parse(missingPage));
    }

    private static CatalogVersionSnapshot snapshot() {
        CatalogVersion version = new CatalogVersion(
                2, CatalogVersionStatus.DRAFT, 1L, 4, "Draft", 7, Instant.parse("2026-08-02T09:00:00Z"), null, null);
        CatalogPageSnapshot page = new CatalogPageSnapshot(
                CatalogPageType.NORMAL,
                17,
                -1,
                "page",
                "Page",
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
                true,
                false,
                "NORMAL",
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
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", 17, "offer", 10, 0, 0, 1, 0, 1, -1, 0, "", true, false);
        return new CatalogVersionSnapshot(version, List.of(page), List.of(offer));
    }
}
