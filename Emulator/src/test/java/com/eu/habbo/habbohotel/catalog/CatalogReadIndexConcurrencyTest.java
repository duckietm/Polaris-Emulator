package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.Habbo;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;

/**
 * Concurrency smoke test: readers keep returning consistent results while, at the same time, the
 * page map is repeatedly swapped, the read index invalidated, and a page's parent edited through
 * {@link CatalogAdminCacheSync#reparentPage}. Four reader threads hammer {@link
 * CatalogManager#getCatalogItem(int)} and {@link CatalogManager#getCatalogPages(int, Habbo,
 * CatalogPageType)}, a swapper thread races them with {@link CatalogManager#replacePageMap} plus
 * {@link CatalogManager#markCatalogChanged()}, and a reparenter thread races them with {@link
 * CatalogAdminCacheSync#reparentPage} - the source of the {@code ConcurrentModificationException}
 * that {@link CatalogManager#readIndex()} now retries around, since {@link
 * CatalogPage#getChildPages()} is a plain, unsynchronized {@code HashMap}.
 */
class CatalogReadIndexConcurrencyTest {

    private static final int PAGE_COUNT = 50;
    private static final int ITEMS_PER_PAGE = 20;
    private static final int ITEM_COUNT = PAGE_COUNT * ITEMS_PER_PAGE;
    private static final int READER_THREADS = 4;
    private static final long RACE_DURATION_MILLIS = 300;

    /** Flipped by the main thread once the race window has elapsed; read by the racing threads. */
    private static final class StopSignal {
        volatile boolean stop;
    }

    @Test
    @Timeout(20)
    void readersSurviveConcurrentPageMapSwapsAndChildEdits() throws Exception {
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

        StopSignal stopSignal = new StopSignal();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Thread[] readers = new Thread[READER_THREADS];
        for (int i = 0; i < READER_THREADS; i++) {
            readers[i] = new Thread(() -> runReader(manager, habbo, failures), "catalog-reader-" + i);
        }

        Thread swapper = new Thread(() -> runSwapper(manager, root, pages, stopSignal), "catalog-swapper");
        Thread reparenter = new Thread(
                () -> runReparenter(manager, pages.get(0), pages.get(1), stopSignal, failures), "catalog-reparenter");

        for (Thread reader : readers) {
            reader.start();
        }
        swapper.start();
        reparenter.start();

        Thread.sleep(RACE_DURATION_MILLIS);
        stopSignal.stop = true;

        for (Thread reader : readers) {
            reader.join();
        }
        swapper.join();
        reparenter.join();

        assertTrue(failures.isEmpty(), () -> "racing threads raised: " + failures);
        assertNotNull(manager.getCatalogItem(1));
    }

    private static void runReader(CatalogManager manager, Habbo habbo, List<Throwable> failures) {
        Random random = new Random();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RACE_DURATION_MILLIS);

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

    private static void runSwapper(
            CatalogManager manager,
            CatalogReadFixture.TestPage root,
            List<CatalogReadFixture.TestPage> pages,
            StopSignal stopSignal) {
        while (!stopSignal.stop) {
            Int2ObjectOpenHashMap<CatalogPage> fresh = new Int2ObjectOpenHashMap<>();
            fresh.put(-1, root);
            for (CatalogReadFixture.TestPage page : pages) {
                fresh.put(page.getId(), page);
            }

            CatalogManager.replacePageMap(manager.catalogPages, fresh);
            manager.markCatalogChanged();
            Thread.yield();
        }
    }

    /**
     * Reparents {@code moved} back and forth between the root and {@code sibling} through the
     * same admin entry point production code uses, mutating {@code getChildPages()} on both
     * parents underneath any concurrent {@link CatalogReadIndex#build}. The static mock is scoped
     * to this thread only - Mockito's static mocking is thread-confined - and no other thread in
     * this test touches {@link Emulator}, so it never contends with another registration.
     */
    private static void runReparenter(
            CatalogManager manager,
            CatalogReadFixture.TestPage moved,
            CatalogReadFixture.TestPage sibling,
            StopSignal stopSignal,
            List<Throwable> failures) {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            GameEnvironment gameEnvironment = mock(GameEnvironment.class);
            when(gameEnvironment.getCatalogManager()).thenReturn(manager);
            emulator.when(Emulator::getGameEnvironment).thenReturn(gameEnvironment);

            while (!stopSignal.stop) {
                try {
                    CatalogAdminCacheSync.reparentPage(moved, sibling.getId(), 0, CatalogPageType.NORMAL);
                    CatalogAdminCacheSync.reparentPage(moved, -1, 0, CatalogPageType.NORMAL);
                } catch (Throwable t) {
                    failures.add(t);
                }
            }
        }
    }
}
