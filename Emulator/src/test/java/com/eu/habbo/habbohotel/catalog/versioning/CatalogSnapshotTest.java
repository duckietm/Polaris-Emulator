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
    void preservesLegacyNegativePageOrder() {
        CatalogPageSnapshot page = page(CatalogPageType.NORMAL, 96, -1);

        assertEquals(-1, page.orderNum());
    }

    @Test
    void preservesLegacyNegativeOfferOrder() {
        CatalogOfferSnapshot offer = offer(CatalogPageType.NORMAL, 26810468, -1000);

        assertEquals(-1000, offer.orderNumber());
    }

    @Test
    void preservesLegacyZeroOfferAmount() {
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", 17, "rare_dragon", 25, 0, 0, 0, 0, 1, -1, 0, "", true, false);

        assertEquals(0, offer.amount());
    }

    @Test
    void preservesLegacyNegativeOfferPageId() {
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", -1, "rare_dragon", 25, 0, 0, 1, 0, 1, -1, 0, "", true, false);

        assertEquals(-1, offer.pageId());
    }

    @Test
    void preservesLegacyNegativeOfferLimitedStack() {
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", 17, "rare_dragon", 25, 0, 0, 1, -5, 1, -1, 0, "", true, false);

        assertEquals(-5, offer.limitedStack());
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
        return page(catalogType, pageId, 1);
    }

    private static CatalogPageSnapshot page(CatalogPageType catalogType, int pageId, int orderNum) {
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
                orderNum,
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
        return offer(catalogType, offerId, 1);
    }

    private static CatalogOfferSnapshot offer(CatalogPageType catalogType, int offerId, int orderNumber) {
        return new CatalogOfferSnapshot(
                catalogType, offerId, "12", 17, "rare_dragon", 25, 0, 0, 1, 0, orderNumber, -1, 0, "", true, false);
    }
}
