package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogValidatorTest {

    @Test
    void acceptsAConsistentCatalogSnapshot() {
        CatalogVersionSnapshot snapshot = snapshot(
                List.of(page(1, -1, 0, true, true, "root", "2"), page(2, 1, 0, true, true, "default_3x3", "")),
                List.of(offer(10, 2, "100;101:2", 5, 0, 0, 20)));
        CatalogValidator validator = validator(Set.of(100, 101), Set.of(5), Map.of(10, 4));

        CatalogValidationReport report = validator.validate(snapshot);

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void reportsStructuralReferenceAndCommercialErrorsTogether() {
        CatalogVersionSnapshot snapshot = snapshot(
                List.of(
                        page(1, -1, 0, false, false, "root", "999;broken"),
                        page(2, 3, 1, true, true, "not_a_layout", ""),
                        page(3, 2, 1, true, true, "default_3x3", ""),
                        page(4, 1, 0, true, true, "default_3x3", ""),
                        page(5, 404, 0, true, true, "default_3x3", ""),
                        page(6, 1, 0, true, true, "default_3x3", "")),
                List.of(offer(10, 2, "100;404:2", -1, 3, 77, 2), offer(11, 999, "100", 0, 0, 0, 0)));
        CatalogValidator validator = validator(Set.of(100), Set.of(5), Map.of(10, 3));

        CatalogValidationReport report = validator.validate(snapshot);

        assertFalse(report.valid());
        assertCodes(
                report,
                "PAGE_PARENT_MISSING",
                "PAGE_CYCLE",
                "PAGE_SIBLING_ORDER_DUPLICATE",
                "PAGE_INCLUDE_INVALID",
                "PAGE_INCLUDE_MISSING",
                "PAGE_LAYOUT_UNSUPPORTED",
                "PAGE_ANCESTOR_NOT_AVAILABLE",
                "OFFER_PAGE_MISSING",
                "OFFER_ITEM_MISSING",
                "OFFER_PRICE_INVALID",
                "OFFER_CURRENCY_UNSUPPORTED",
                "OFFER_LIMITED_STACK_BELOW_SALES");
    }

    private static CatalogValidator validator(
            Set<Integer> itemIds, Set<Integer> currencyTypes, Map<Integer, Integer> liveLimitedSells) {
        return new CatalogValidator(itemIds, currencyTypes, liveLimitedSells, Set.of("root", "default_3x3"));
    }

    private static void assertCodes(CatalogValidationReport report, String... expectedCodes) {
        Set<String> actual =
                report.issues().stream().map(CatalogValidationIssue::code).collect(java.util.stream.Collectors.toSet());
        for (String expectedCode : expectedCodes) {
            assertTrue(actual.contains(expectedCode), () -> "Missing " + expectedCode + " in " + actual);
        }
        assertEquals(expectedCodes.length, actual.size(), () -> "Unexpected codes: " + actual);
    }

    private static CatalogVersionSnapshot snapshot(List<CatalogPageSnapshot> pages, List<CatalogOfferSnapshot> offers) {
        return new CatalogVersionSnapshot(
                new CatalogVersion(
                        2,
                        CatalogVersionStatus.DRAFT,
                        1L,
                        7,
                        "Shared draft",
                        7,
                        Instant.parse("2026-08-02T11:00:00Z"),
                        null,
                        null),
                pages,
                offers);
    }

    private static CatalogPageSnapshot page(
            int id, int parentId, int order, boolean visible, boolean enabled, String layout, String includes) {
        return new CatalogPageSnapshot(
                id,
                parentId,
                "page_" + id,
                "Page " + id,
                layout,
                0,
                0,
                1,
                order,
                visible,
                enabled,
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
                includes);
    }

    private static CatalogOfferSnapshot offer(
            int id, int pageId, String itemIds, int credits, int points, int pointsType, int limitedStack) {
        return new CatalogOfferSnapshot(
                id,
                itemIds,
                pageId,
                "Offer " + id,
                credits,
                points,
                pointsType,
                1,
                limitedStack,
                id,
                id,
                0,
                "",
                true,
                false);
    }
}
