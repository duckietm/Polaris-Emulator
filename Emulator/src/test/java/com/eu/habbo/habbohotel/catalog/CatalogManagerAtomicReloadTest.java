package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CatalogManagerAtomicReloadTest {
    @Test
    void pageMapReplacementNeverExposesTheClearedIntermediateState() throws Exception {
        CatalogPage oldPage = mock(CatalogPage.class);
        CatalogPage nextPage = mock(CatalogPage.class);
        Int2ObjectMap<CatalogPage> live = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        live.put(1, oldPage);

        CountDownLatch copyStarted = new CountDownLatch(1);
        CountDownLatch allowCopy = new CountDownLatch(1);
        Map<Integer, CatalogPage> replacement = new AbstractMap<>() {
            @Override
            public Set<Entry<Integer, CatalogPage>> entrySet() {
                copyStarted.countDown();
                try {
                    if (!allowCopy.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting to copy");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return Map.of(2, nextPage).entrySet();
            }
        };

        CompletableFuture<Void> reload = CompletableFuture.runAsync(
                () -> CatalogManager.replacePageMap(live, replacement));
        if (!copyStarted.await(5, TimeUnit.SECONDS)) throw new AssertionError("Reload did not start");

        CompletableFuture<Integer> observedSize = CompletableFuture.supplyAsync(live::size);
        Thread.sleep(50);
        assertFalse(observedSize.isDone(), "catalog readers must wait for the complete replacement");

        allowCopy.countDown();
        reload.get(5, TimeUnit.SECONDS);
        assertEquals(1, observedSize.get(5, TimeUnit.SECONDS));
        assertEquals(nextPage, live.get(2));
    }
}
