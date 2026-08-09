package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveDraft;

final class CatalogAdminPageDraftChecks {
    private static final int ROOT_PARENT_ID = -1;
    private static final int MAX_PARENT_WALK = 64;

    private CatalogAdminPageDraftChecks() {}

    static void validateParentAndIncludes(
            CatalogSmartSaveDraft draft, CatalogPageType type, int pageId, int parentId, String includes) {
        if (parentId != ROOT_PARENT_ID) {
            if (parentId == pageId) {
                throw new IllegalArgumentException("A page cannot be its own parent");
            }
            if (draft.page(type, parentId).isEmpty()) {
                throw new IllegalArgumentException("Parent page not found in shared draft: " + parentId);
            }
            if (pageId > 0 && wouldCreateCycle(draft, type, pageId, parentId)) {
                throw new IllegalArgumentException("Refusing to re-parent: that would create a cycle");
            }
        }

        if (includes == null || includes.isEmpty()) return;
        for (String entry : includes.split(";")) {
            int includedPageId = Integer.parseInt(entry);
            if (includedPageId == pageId || draft.page(type, includedPageId).isEmpty()) {
                throw new IllegalArgumentException("Included pages must exist and cannot include the current page");
            }
        }
    }

    private static boolean wouldCreateCycle(
            CatalogSmartSaveDraft draft, CatalogPageType type, int pageId, int parentId) {
        int current = parentId;
        for (int hops = 0; hops < MAX_PARENT_WALK; hops++) {
            if (current == ROOT_PARENT_ID) return false;
            if (current == pageId) return true;
            CatalogPageSnapshot parent = draft.page(type, current).orElse(null);
            if (parent == null) return false;
            current = parent.parentId();
        }
        return true;
    }
}
