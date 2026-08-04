package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CatalogPageMovePlanner {
    private static final Comparator<CatalogPageSnapshot> PAGE_ORDER =
            Comparator.comparingInt(CatalogPageSnapshot::orderNum).thenComparingInt(CatalogPageSnapshot::pageId);

    private CatalogPageMovePlanner() {}

    public static List<CatalogPageSnapshot> plan(
            List<CatalogPageSnapshot> pages, CatalogPageType catalogType, int pageId, int newParentId, int newIndex) {
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(catalogType, "catalogType");

        CatalogPageSnapshot moved = pages.stream()
                .filter(page -> page.catalogType() == catalogType && page.pageId() == pageId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Catalog page not found: " + pageId));

        Map<Integer, CatalogPageSnapshot> changes = new LinkedHashMap<>();
        if (moved.parentId() == newParentId) {
            List<CatalogPageSnapshot> siblings = siblings(pages, catalogType, newParentId, pageId);
            siblings.add(Math.min(Math.max(newIndex, 0), siblings.size()), moved);
            normalize(siblings, newParentId, changes);
        } else {
            normalize(siblings(pages, catalogType, moved.parentId(), pageId), moved.parentId(), changes);

            List<CatalogPageSnapshot> targetSiblings = siblings(pages, catalogType, newParentId, pageId);
            targetSiblings.add(Math.min(Math.max(newIndex, 0), targetSiblings.size()), moved);
            normalize(targetSiblings, newParentId, changes);
        }

        changes.entrySet()
                .removeIf(entry -> pages.stream()
                        .anyMatch(page -> page.catalogType() == catalogType
                                && page.pageId() == entry.getKey()
                                && page.equals(entry.getValue())));
        return List.copyOf(changes.values());
    }

    private static List<CatalogPageSnapshot> siblings(
            List<CatalogPageSnapshot> pages, CatalogPageType catalogType, int parentId, int excludedPageId) {
        return new ArrayList<>(pages.stream()
                .filter(page -> page.catalogType() == catalogType
                        && page.parentId() == parentId
                        && page.pageId() != excludedPageId)
                .sorted(PAGE_ORDER)
                .toList());
    }

    private static void normalize(
            List<CatalogPageSnapshot> siblings, int parentId, Map<Integer, CatalogPageSnapshot> changes) {
        for (int index = 0; index < siblings.size(); index++) {
            CatalogPageSnapshot page = siblings.get(index);
            changes.put(page.pageId(), CatalogSnapshotPatch.movePage(page, parentId, index));
        }
    }
}
