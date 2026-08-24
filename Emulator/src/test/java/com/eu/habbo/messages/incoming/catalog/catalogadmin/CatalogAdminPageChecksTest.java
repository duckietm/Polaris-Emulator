package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogAdminPageChecksTest {

    @Test
    void acceptsRootPlacementAndExistingIncludedPages() {
        CatalogVersionSnapshot live = mock(CatalogVersionSnapshot.class);
        when(live.page(CatalogPageType.NORMAL, 20)).thenReturn(Optional.of(page(20, -1)));

        assertDoesNotThrow(
                () -> CatalogAdminPageChecks.validateParentAndIncludes(live, CatalogPageType.NORMAL, 0, -1, "20"));
    }

    @Test
    void rejectsMissingParentsAndIncludedPages() {
        CatalogVersionSnapshot live = mock(CatalogVersionSnapshot.class);
        when(live.page(CatalogPageType.NORMAL, 30)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogAdminPageChecks.validateParentAndIncludes(live, CatalogPageType.NORMAL, 17, 30, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogAdminPageChecks.validateParentAndIncludes(live, CatalogPageType.NORMAL, 17, -1, "30"));
    }

    @Test
    void rejectsSelfParentingAndIndirectCycles() {
        CatalogVersionSnapshot live = mock(CatalogVersionSnapshot.class);
        when(live.page(CatalogPageType.NORMAL, 20)).thenReturn(Optional.of(page(20, 17)));

        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogAdminPageChecks.validateParentAndIncludes(live, CatalogPageType.NORMAL, 17, 17, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogAdminPageChecks.validateParentAndIncludes(live, CatalogPageType.NORMAL, 17, 20, ""));
    }

    private static CatalogPageSnapshot page(int id, int parentId) {
        return new CatalogPageSnapshot(
                id,
                parentId,
                "page_" + id,
                "Page " + id,
                "default_3x3",
                1,
                1,
                1,
                1,
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
