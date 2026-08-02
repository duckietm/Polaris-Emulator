package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogPageMovePlannerTest {
    @Test
    void reordersEverySiblingWithoutDuplicatePositions() {
        List<CatalogPageSnapshot> pages = List.of(page(1, 10, 0), page(2, 10, 1), page(3, 10, 2));

        List<CatalogPageSnapshot> result =
                apply(pages, CatalogPageMovePlanner.plan(pages, CatalogPageType.NORMAL, 3, 10, 0));

        assertEquals(List.of(3, 1, 2), orderedIds(result, 10));
        assertEquals(List.of(0, 1, 2), orderedPositions(result, 10));
    }

    @Test
    void normalizesBothParentsWhenReparenting() {
        List<CatalogPageSnapshot> pages = List.of(page(1, 10, 0), page(2, 10, 1), page(3, 20, 0), page(4, 20, 1));

        List<CatalogPageSnapshot> result =
                apply(pages, CatalogPageMovePlanner.plan(pages, CatalogPageType.NORMAL, 1, 20, 1));

        assertEquals(List.of(2), orderedIds(result, 10));
        assertEquals(List.of(0), orderedPositions(result, 10));
        assertEquals(List.of(3, 1, 4), orderedIds(result, 20));
        assertEquals(List.of(0, 1, 2), orderedPositions(result, 20));
    }

    private static List<CatalogPageSnapshot> apply(List<CatalogPageSnapshot> pages, List<CatalogPageSnapshot> changes) {
        List<CatalogPageSnapshot> result = new ArrayList<>(pages);
        for (CatalogPageSnapshot change : changes) {
            int index = result.indexOf(result.stream()
                    .filter(page -> page.catalogType() == change.catalogType() && page.pageId() == change.pageId())
                    .findFirst()
                    .orElseThrow());
            result.set(index, change);
        }
        return result;
    }

    private static List<Integer> orderedIds(List<CatalogPageSnapshot> pages, int parentId) {
        return pages.stream()
                .filter(page -> page.parentId() == parentId)
                .sorted(Comparator.comparingInt(CatalogPageSnapshot::orderNum)
                        .thenComparingInt(CatalogPageSnapshot::pageId))
                .map(CatalogPageSnapshot::pageId)
                .toList();
    }

    private static List<Integer> orderedPositions(List<CatalogPageSnapshot> pages, int parentId) {
        return pages.stream()
                .filter(page -> page.parentId() == parentId)
                .sorted(Comparator.comparingInt(CatalogPageSnapshot::orderNum)
                        .thenComparingInt(CatalogPageSnapshot::pageId))
                .map(CatalogPageSnapshot::orderNum)
                .toList();
    }

    private static CatalogPageSnapshot page(int id, int parentId, int orderNum) {
        return new CatalogPageSnapshot(
                id,
                parentId,
                "page-" + id,
                "Page " + id,
                "DEFAULT_3X3",
                0,
                0,
                1,
                orderNum,
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
    }
}
