package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogSnapshotTest {

    @Test
    void indexesPagesAndOffersByTheirStableIds() {
        CatalogVersionSnapshot snapshot = new CatalogVersionSnapshot(version(), List.of(page(17)), List.of(offer(42)));

        assertEquals("Rare page", snapshot.page(17).orElseThrow().caption());
        assertEquals("rare_dragon", snapshot.offer(42).orElseThrow().catalogName());
        assertTrue(snapshot.page(999).isEmpty());
        assertTrue(snapshot.offer(999).isEmpty());
    }

    @Test
    void rejectsDuplicateStableIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogVersionSnapshot(version(), List.of(page(17), page(17)), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogVersionSnapshot(version(), List.of(), List.of(offer(42), offer(42))));
    }

    @Test
    void exposesImmutableSnapshotCollections() {
        CatalogVersionSnapshot snapshot = new CatalogVersionSnapshot(version(), List.of(page(17)), List.of(offer(42)));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.pages().add(page(18)));
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.offers().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.pageIndex().clear());
        assertFalse(snapshot.pages().isEmpty());
    }

    @Test
    void keepsNormalAndBuildersClubEntitiesWithTheSameStableIdSeparate() {
        CatalogVersionSnapshot snapshot = new CatalogVersionSnapshot(
                version(),
                List.of(page(CatalogPageType.NORMAL, 17), page(CatalogPageType.BUILDER, 17)),
                List.of(offer(CatalogPageType.NORMAL, 42), offer(CatalogPageType.BUILDER, 42)));

        assertEquals(
                "NORMAL",
                snapshot.page(CatalogPageType.NORMAL, 17)
                        .orElseThrow()
                        .catalogType()
                        .name());
        assertEquals(
                "BUILDER",
                snapshot.page(CatalogPageType.BUILDER, 17)
                        .orElseThrow()
                        .catalogType()
                        .name());
        assertEquals(
                "NORMAL",
                snapshot.offer(CatalogPageType.NORMAL, 42)
                        .orElseThrow()
                        .catalogType()
                        .name());
        assertEquals(
                "BUILDER",
                snapshot.offer(CatalogPageType.BUILDER, 42)
                        .orElseThrow()
                        .catalogType()
                        .name());
    }

    @Test
    void catalogLocksAreScopedByCatalogType() {
        CatalogLockKey normal = new CatalogLockKey(CatalogEntityType.PAGE, CatalogPageType.NORMAL, 17);
        CatalogLockKey builder = new CatalogLockKey(CatalogEntityType.PAGE, CatalogPageType.BUILDER, 17);

        assertFalse(normal.equals(builder));
    }

    private static CatalogVersion version() {
        return new CatalogVersion(
                2,
                CatalogVersionStatus.DRAFT,
                1L,
                4,
                "Shared draft",
                7,
                Instant.parse("2026-08-02T09:00:00Z"),
                null,
                null);
    }

    private static CatalogPageSnapshot page(int pageId) {
        return page(CatalogPageType.NORMAL, pageId);
    }

    private static CatalogPageSnapshot page(CatalogPageType catalogType, int pageId) {
        return new CatalogPageSnapshot(
                catalogType,
                pageId,
                -1,
                "rare_page",
                "Rare page",
                "default_3x3",
                1,
                145,
                1,
                1,
                true,
                true,
                false,
                "NORMAL",
                false,
                "rare_headline",
                "rare_teaser",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }

    private static CatalogOfferSnapshot offer(int offerId) {
        return offer(CatalogPageType.NORMAL, offerId);
    }

    private static CatalogOfferSnapshot offer(CatalogPageType catalogType, int offerId) {
        return new CatalogOfferSnapshot(
                catalogType, offerId, "12", 17, "rare_dragon", 25, 0, 0, 1, 0, 1, -1, 0, "", true, false);
    }
}
