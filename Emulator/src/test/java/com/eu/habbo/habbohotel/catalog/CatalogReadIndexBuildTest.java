package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eu.habbo.messages.ServerMessage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for {@link CatalogReadIndex#build}: it must read a page's items through
 * {@link CatalogPage#catalogItemsView()}, never the overridable {@link
 * CatalogPage#getCatalogItems()}. {@code RoomBundleLayout} overrides {@code getCatalogItems()}
 * with room-loading DB I/O; calling it for every bundle page on every rebuild, while the build
 * holds {@code CatalogManager}'s page map lock and read-index lock, was the bug.
 */
class CatalogReadIndexBuildTest {

    @Test
    void buildNeverCallsTheOverridableGetCatalogItems() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();

        CatalogReadFixture.TestPage root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);

        AtomicInteger calls = new AtomicInteger();
        CountingPage page = new CountingPage(10, -1, calls);
        manager.getCatalogPagesMap(CatalogPageType.NORMAL).put(page.getId(), page);
        root.addChildPage(page);

        CatalogItem item = CatalogReadFixture.item(1, page.getId(), 0);
        page.addItem(item);

        manager.markCatalogChanged();
        manager.readIndex();

        assertEquals(0, calls.get(), "CatalogReadIndex.build must not call the overridable getCatalogItems()");
        assertSame(item, manager.readIndex().item(CatalogPageType.NORMAL, 1));
    }

    /** A {@link CatalogPage} whose overridable {@link #getCatalogItems()} counts every call. */
    private static final class CountingPage extends CatalogPage {
        private final AtomicInteger calls;

        CountingPage(int id, int parentId, AtomicInteger calls) {
            this.id = id;
            this.parentId = parentId;
            this.visible = true;
            this.enabled = true;
            this.calls = calls;
        }

        @Override
        public Int2ObjectMap<CatalogItem> getCatalogItems() {
            this.calls.incrementAndGet();
            return super.getCatalogItems();
        }

        @Override
        public void serialize(ServerMessage message) {}
    }
}
