package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogDraftPreviewServiceTest {
    @Test
    void evaluatesDraftVisibilityAndPurchaseEligibilityForTheSimulatedPersona() {
        CatalogVersionSnapshot draft = new CatalogVersionSnapshot(
                version(),
                List.of(page(CatalogPageType.NORMAL, 1, 5, true), page(CatalogPageType.BUILDER, 1, 1, true)),
                List.of(offer(CatalogPageType.NORMAL, 10, 1, 50), offer(CatalogPageType.BUILDER, 10, 1, 0)));
        CatalogPreviewPersona guest = new CatalogPreviewPersona(1, false, false, false, false, 20, Map.of());

        CatalogDraftPreview guestPreview = new CatalogDraftPreviewService().preview(draft, guest);

        assertTrue(guestPreview.pages().isEmpty());
        assertTrue(guestPreview.offers().isEmpty());

        CatalogPreviewPersona builderAdmin = new CatalogPreviewPersona(7, true, true, true, true, 100, Map.of());
        CatalogDraftPreview fullPreview = new CatalogDraftPreviewService().preview(draft, builderAdmin);

        assertEquals(2, fullPreview.pages().size());
        assertEquals(2, fullPreview.offers().size());
        assertTrue(fullPreview.offers().stream().allMatch(CatalogPreviewOffer::eligible));
        assertEquals(99, fullPreview.revision());
    }

    @Test
    void explainsWhyAnOfferCannotBePurchased() {
        CatalogVersionSnapshot draft = new CatalogVersionSnapshot(
                version(),
                List.of(page(CatalogPageType.NORMAL, 1, 1, true)),
                List.of(offer(CatalogPageType.NORMAL, 10, 1, 50)));

        CatalogDraftPreview preview = new CatalogDraftPreviewService()
                .preview(draft, new CatalogPreviewPersona(1, false, false, false, false, 5, Map.of()));

        CatalogPreviewOffer offer = preview.offers().getFirst();
        assertFalse(offer.eligible());
        assertTrue(offer.reasons().contains("INSUFFICIENT_CREDITS"));
    }

    private static CatalogVersion version() {
        return new CatalogVersion(
                2, CatalogVersionStatus.DRAFT, 1L, 99, "Draft", 7, Instant.parse("2026-08-02T09:00:00Z"), null, null);
    }

    private static CatalogPageSnapshot page(CatalogPageType type, int id, int rank, boolean visible) {
        return new CatalogPageSnapshot(
                type,
                id,
                -1,
                "page",
                "Page",
                "default_3x3",
                1,
                1,
                rank,
                1,
                visible,
                true,
                false,
                type.name(),
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
    }

    private static CatalogOfferSnapshot offer(CatalogPageType type, int id, int pageId, int credits) {
        return new CatalogOfferSnapshot(
                type, id, "12", pageId, "offer", credits, 0, 0, 1, 0, 1, -1, 0, "", true, false);
    }
}
