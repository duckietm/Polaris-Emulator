package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A page's children are edited by the admin studio while readers copy them to build the read index, with no monitor
 * between the two. A plain map broke the copy: {@code new ArrayList<>(values())} sized its array before an edit added
 * a child and then overran it, which no retry caught.
 */
class CatalogPageChildPagesTest {

    @Test
    void aChildAddedWhileTheChildrenAreWalkedDoesNotBreakTheWalk() {
        CatalogPage parent = page(1, -1);
        for (int id = 2; id <= 20; id++) parent.addChildPage(page(id, 1));

        List<CatalogPage> walked = new ArrayList<>();
        assertDoesNotThrow(() -> {
            for (CatalogPage child : parent.getChildPages().values()) {
                walked.add(child);
                if (walked.size() == 5) parent.addChildPage(page(99, 1));
            }
        });
        assertEquals(20, parent.getChildPages().size());
    }

    @Test
    void aChildRemovedWhileTheChildrenAreWalkedDoesNotBreakTheWalk() {
        CatalogPage parent = page(1, -1);
        for (int id = 2; id <= 20; id++) parent.addChildPage(page(id, 1));

        assertDoesNotThrow(() -> {
            for (CatalogPage child : parent.getChildPages().values()) {
                if (child.getId() == 5) parent.getChildPages().remove(12);
            }
        });
        assertEquals(18, parent.getChildPages().size());
    }

    private static CatalogPage page(int id, int parentId) {
        return CatalogReadFixture.page(
                id, parentId, id, true, true, false, 0, "page_" + id, null, CatalogPageType.NORMAL);
    }
}
