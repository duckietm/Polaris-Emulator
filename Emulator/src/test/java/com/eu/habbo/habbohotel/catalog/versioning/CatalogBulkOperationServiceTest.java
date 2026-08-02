package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogBulkOperationServiceTest {
    private final CatalogBulkOperationService service = new CatalogBulkOperationService(new Gson());

    @Test
    void dryRunChangesSeveralPricesWithoutMutatingTheSnapshot() {
        CatalogVersionSnapshot draft = snapshot();
        CatalogBulkRequest request = new CatalogBulkRequest(
                CatalogBulkOperation.PRICE_PERCENT, CatalogPageType.NORMAL, List.of(10, 11), 50, false);

        CatalogBulkDryRun result = service.dryRun(draft, request);

        assertEquals(2, result.changes().size());
        assertEquals(10, draft.offer(CatalogPageType.NORMAL, 10).orElseThrow().costCredits());
        assertTrue(result.fingerprint().matches("[0-9a-f]{64}"));
        assertEquals(
                15,
                new Gson()
                        .fromJson(result.changes().getFirst().afterJson(), CatalogOfferSnapshot.class)
                        .costCredits());
    }

    @Test
    void requiresTheSameFingerprintBeforeApply() {
        CatalogVersionSnapshot draft = snapshot();
        CatalogBulkRequest request = new CatalogBulkRequest(
                CatalogBulkOperation.SET_VISIBILITY, CatalogPageType.NORMAL, List.of(1), 0, false);
        CatalogBulkDryRun result = service.dryRun(draft, request);

        assertEquals(result.changes(), service.confirm(draft, request, result.fingerprint()));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(draft, request, "stale"));
    }

    private static CatalogVersionSnapshot snapshot() {
        CatalogVersion version = new CatalogVersion(
                2, CatalogVersionStatus.DRAFT, 1L, 4, "Draft", 7, Instant.parse("2026-08-02T09:00:00Z"), null, null);
        CatalogPageSnapshot page = new CatalogPageSnapshot(
                CatalogPageType.NORMAL,
                1,
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
        CatalogOfferSnapshot offer1 = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 10, "12", 1, "one", 10, 0, 0, 1, 0, 1, -1, 0, "", true, false);
        CatalogOfferSnapshot offer2 = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 11, "13", 1, "two", 20, 0, 0, 1, 0, 2, -1, 0, "", true, false);
        return new CatalogVersionSnapshot(version, List.of(page), List.of(offer1, offer2));
    }
}
