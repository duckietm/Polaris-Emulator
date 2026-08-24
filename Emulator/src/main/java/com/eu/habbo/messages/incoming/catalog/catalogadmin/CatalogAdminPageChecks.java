package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot;

final class CatalogAdminPageChecks {
    private static final int ROOT_PARENT_ID = -1;
    private static final int MAX_PARENT_WALK = 64;

    private CatalogAdminPageChecks() {}

    static void validateParentAndIncludes(
            CatalogVersionSnapshot live, CatalogPageType type, int pageId, int parentId, String includes) {
        validateParentAndIncludes(live::page, type, pageId, parentId, includes);
    }

    private static void validateParentAndIncludes(
            PageLookup pages, CatalogPageType type, int pageId, int parentId, String includes) {
        if (parentId != ROOT_PARENT_ID) {
            if (parentId == pageId) {
                throw new IllegalArgumentException("A page cannot be its own parent");
            }
            if (pages.page(type, parentId).isEmpty()) {
                throw new IllegalArgumentException("Parent page not found in live catalog: " + parentId);
            }
            if (pageId > 0 && wouldCreateCycle(pages, type, pageId, parentId)) {
                throw new IllegalArgumentException("Refusing to re-parent: that would create a cycle");
            }
        }

        if (includes == null || includes.isEmpty()) return;
        for (String entry : includes.split(";")) {
            int includedPageId = Integer.parseInt(entry);
            if (includedPageId == pageId || pages.page(type, includedPageId).isEmpty()) {
                throw new IllegalArgumentException("Included pages must exist and cannot include the current page");
            }
        }
    }

    private static boolean wouldCreateCycle(PageLookup pages, CatalogPageType type, int pageId, int parentId) {
        int current = parentId;
        for (int hops = 0; hops < MAX_PARENT_WALK; hops++) {
            if (current == ROOT_PARENT_ID) return false;
            if (current == pageId) return true;
            CatalogPageSnapshot parent = pages.page(type, current).orElse(null);
            if (parent == null) return false;
            current = parent.parentId();
        }
        return true;
    }

    @FunctionalInterface
    private interface PageLookup {
        java.util.Optional<CatalogPageSnapshot> page(CatalogPageType type, int pageId);
    }
}
