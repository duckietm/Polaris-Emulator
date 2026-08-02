package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogSnapshotPatchTest {
    private static final CatalogPageSnapshot PAGE = new CatalogPageSnapshot(
            10,
            -1,
            "page-save",
            "Page",
            "DEFAULT_3X3",
            1,
            2,
            1,
            3,
            true,
            true,
            false,
            "NORMAL",
            false,
            "headline",
            "teaser",
            "special",
            "text1",
            "text2",
            "details",
            "text teaser",
            0,
            "");

    @Test
    void replacesOnlyThePageIcon() {
        CatalogPageSnapshot patched = CatalogSnapshotPatch.setPageIcon(PAGE, 8);

        assertEquals(8, patched.iconImage());
        assertEquals(PAGE.pageHeadline(), patched.pageHeadline());
        assertEquals(PAGE.pageTeaser(), patched.pageTeaser());
    }

    @Test
    void replacesOnlyThePageImages() {
        CatalogPageSnapshot patched = CatalogSnapshotPatch.setPageImages(PAGE, "new-headline", "new-teaser");

        assertEquals("new-headline", patched.pageHeadline());
        assertEquals("new-teaser", patched.pageTeaser());
        assertEquals(PAGE.iconImage(), patched.iconImage());
    }
}
