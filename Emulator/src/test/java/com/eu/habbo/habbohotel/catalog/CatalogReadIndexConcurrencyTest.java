package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.users.Habbo;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Concurrency smoke test: readers keep returning consistent results while the page map is
 * repeatedly swapped and the read index invalidated underneath them. Four reader threads hammer
 * {@link CatalogManager#getCatalogItem(int)} and {@link CatalogManager#getCatalogPages(int,
 * Habbo, CatalogPageType)} for 300 ms while the main thread races them with {@link
 * CatalogManager#replacePageMap} plus {@link CatalogManager#markCatalogChanged()}.
 */
class CatalogReadIndexConcurrencyTest {

    private static final int PAGE_COUNT = 50;
    private static final int ITEMS_PER_PAGE = 20;
    private static final int ITEM_COUNT = PAGE_COUNT * ITEMS_PER_PAGE;
    private static final int READER_THREADS = 4;
    private static final long READ_DURATION_MILLIS = 300;
    private static final int SWAP_ITERATIONS = 200;

    @Test
    @Timeout(20)
    void readersSurviveConcurrentPageMapSwaps() throws Exception {
        CatalogManager manager = CatalogReadFixture.manager();
        Habbo habbo = CatalogReadFixture.habbo(0, false);

        CatalogReadFixture.TestPage root =
                CatalogReadFixture.page(-1, -2, 0, true, true, false, 0, null, null, CatalogPageType.NORMAL);
        CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, root);

        List<CatalogReadFixture.TestPage> pages = new ArrayList<>(PAGE_COUNT);
        for (int pageIndex = 1; pageIndex <= PAGE_COUNT; pageIndex++) {
            CatalogReadFixture.TestPage page = CatalogReadFixture.page(
                    pageIndex, -1, pageIndex, true, true, false, 0, "page_" + pageIndex, null, CatalogPageType.NORMAL);
            CatalogReadFixture.attach(manager, CatalogPageType.NORMAL, page);
            pages.add(page);

            for (int itemIndex = 0; itemIndex < ITEMS_PER_PAGE; itemIndex++) {
                int itemId = (pageIndex - 1) * ITEMS_PER_PAGE + itemIndex + 1;
                page.addItem(CatalogReadFixture.item(itemId, pageIndex, itemIndex));
            }
        }
        manager.markCatalogChanged();

        assertNotNull(manager.getCatalogItem(1));

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        Thread[] readers = new Thread[READER_THREADS];
        for (int i = 0; i < READER_THREADS; i++) {
            readers[i] = new Thread(() -> runReader(manager, habbo, failures), "catalog-reader-" + i);
        }
        for (Thread reader : readers) {
            reader.start();
        }

        for (int i = 0; i < SWAP_ITERATIONS; i++) {
            Int2ObjectOpenHashMap<CatalogPage> fresh = new Int2ObjectOpenHashMap<>();
            fresh.put(-1, root);
            for (CatalogReadFixture.TestPage page : pages) {
                fresh.put(page.getId(), page);
            }

            CatalogManager.replacePageMap(manager.catalogPages, fresh);
            manager.markCatalogChanged();
            Thread.yield();
        }

        for (Thread reader : readers) {
            reader.join();
        }

        assertTrue(failures.isEmpty(), () -> "reader threads raised: " + failures);
        assertNotNull(manager.getCatalogItem(1));
    }

    private static void runReader(CatalogManager manager, Habbo habbo, List<Throwable> failures) {
        Random random = new Random();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_DURATION_MILLIS);

        while (System.nanoTime() < deadline) {
            try {
                int id = random.nextInt(ITEM_COUNT) + 1;
                CatalogItem item = manager.getCatalogItem(id);
                if (item != null) {
                    assertEquals(id, item.getId());
                }
                manager.getCatalogPages(-1, habbo, CatalogPageType.NORMAL);
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }
}
