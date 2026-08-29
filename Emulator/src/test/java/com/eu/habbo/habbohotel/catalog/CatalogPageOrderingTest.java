package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogPageOrderingTest {

    /**
     * The stock database ships eight pet pages at order 99 under the same parent, so pages sharing
     * an order slot are the normal case rather than an edge one. They must still come out in one
     * fixed sequence.
     */
    @Test
    void pagesSharingAnOrderSlotComeOutInIdOrder() {
        List<CatalogPage> pages = new ArrayList<>(List.of(page(96, 99), page(90, 99), page(85, 99), page(93, 99)));

        Collections.sort(pages);

        assertEquals(
                List.of(85, 90, 93, 96), pages.stream().map(CatalogPage::getId).toList());
    }

    @Test
    void theOrderSlotStillDecidesFirst() {
        List<CatalogPage> pages = new ArrayList<>(List.of(page(10, 3), page(99, 1), page(50, 2)));

        Collections.sort(pages);

        assertEquals(List.of(99, 50, 10), pages.stream().map(CatalogPage::getId).toList());
    }

    /** The sequence must not depend on the order the pages happened to arrive in. */
    @Test
    void theResultDoesNotDependOnTheInputOrder() {
        List<CatalogPage> one = new ArrayList<>(List.of(page(96, 99), page(85, 99), page(90, 99)));
        List<CatalogPage> other = new ArrayList<>(List.of(page(90, 99), page(96, 99), page(85, 99)));

        Collections.sort(one);
        Collections.sort(other);

        assertEquals(
                one.stream().map(CatalogPage::getId).toList(),
                other.stream().map(CatalogPage::getId).toList());
    }

    private static CatalogPage page(int id, int orderNum) {
        return new TestPage(id, orderNum);
    }

    private static final class TestPage extends CatalogPage {
        private TestPage(int id, int orderNum) {
            this.id = id;
            this.orderNum = orderNum;
        }

        @Override
        public void serialize(com.eu.habbo.messages.ServerMessage message) {
            // not exercised by ordering
        }
    }
}
