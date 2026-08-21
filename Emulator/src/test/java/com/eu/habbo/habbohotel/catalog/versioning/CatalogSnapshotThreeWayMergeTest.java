package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogSnapshotThreeWayMergeTest {
    private final CatalogSnapshotThreeWayMerge merger = new CatalogSnapshotThreeWayMerge(new Gson());

    @Test
    void mergesIndependentLiveAndDraftChanges() {
        CatalogVersionSnapshot base = snapshot(page("Shop"), offer(10));
        CatalogVersionSnapshot live = snapshot(page("Shop"), offer(25));
        CatalogVersionSnapshot draft = snapshot(page("Edited in Studio"), offer(10));

        CatalogSnapshotMergeResult result = merger.merge(base, live, draft);

        assertTrue(result.conflicts().isEmpty());
        assertEquals(
                "Edited in Studio",
                result.snapshot().page(CatalogPageType.NORMAL, 1).orElseThrow().caption());
        assertEquals(
                25,
                result.snapshot()
                        .offer(CatalogPageType.NORMAL, 10)
                        .orElseThrow()
                        .costCredits());
        assertEquals(1, result.importedChanges().size());
        assertEquals(
                CatalogEntityType.OFFER, result.importedChanges().getFirst().entityType());
        assertEquals(
                CatalogChangeOperation.UPDATE,
                result.importedChanges().getFirst().operation());
    }

    @Test
    void mergesDifferentFieldsChangedOnTheSameOffer() {
        CatalogVersionSnapshot base = snapshot(page("Shop"), offer("Chair", 10));
        CatalogVersionSnapshot live = snapshot(page("Shop"), offer("Chair", 25));
        CatalogVersionSnapshot draft = snapshot(page("Shop"), offer("Studio Chair", 10));

        CatalogSnapshotMergeResult result = merger.merge(base, live, draft);

        assertTrue(result.conflicts().isEmpty());
        CatalogOfferSnapshot merged =
                result.snapshot().offer(CatalogPageType.NORMAL, 10).orElseThrow();
        assertEquals("Studio Chair", merged.catalogName());
        assertEquals(25, merged.costCredits());
    }

    @Test
    void reportsTheFieldWhenLiveAndDraftChangedItDifferently() {
        CatalogVersionSnapshot base = snapshot(page("Shop"), offer(10));
        CatalogVersionSnapshot live = snapshot(page("Shop"), offer(25));
        CatalogVersionSnapshot draft = snapshot(page("Shop"), offer(40));

        CatalogSnapshotMergeResult result = merger.merge(base, live, draft);

        assertEquals(1, result.conflicts().size());
        CatalogMergeConflict conflict = result.conflicts().getFirst();
        assertEquals(CatalogEntityType.OFFER, conflict.entityType());
        assertEquals(10, conflict.entityId());
        assertEquals("costCredits", conflict.field());
    }

    private static CatalogVersionSnapshot snapshot(CatalogPageSnapshot page, CatalogOfferSnapshot offer) {
        CatalogVersion version = new CatalogVersion(
                2,
                CatalogVersionStatus.DRAFT,
                1L,
                5,
                "Shared draft",
                7,
                Instant.parse("2026-08-21T10:00:00Z"),
                null,
                null);
        return new CatalogVersionSnapshot(version, List.of(page), List.of(offer));
    }

    private static CatalogPageSnapshot page(String caption) {
        return new CatalogPageSnapshot(
                1, -1, "shop", caption, "root", 0, 0, 1, 0, true, true, false, "NORMAL", false, "", "", "", "", "", "",
                "", 0, "");
    }

    private static CatalogOfferSnapshot offer(int credits) {
        return offer("Chair", credits);
    }

    private static CatalogOfferSnapshot offer(String catalogName, int credits) {
        return new CatalogOfferSnapshot(10, "100", 1, catalogName, credits, 0, 0, 1, 0, 0, 10, 0, "", true, false);
    }
}
