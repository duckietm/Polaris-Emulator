package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPublicCacheCompatibilityTest {

    @Test
    void publicCatalogCachesRemainFinalMutableObjects() throws Exception {
        CatalogManager manager = createManager();

        assertPublicFinalField("giftWrappers");
        assertPublicFinalField("giftFurnis");
        assertPublicFinalField("prizes");
        assertPublicFinalField("targetOffers");
        assertPublicFinalField("clothing");
        assertPublicFinalField("catalogFeaturedPages");

        Map<Integer, Integer> giftWrappers = manager.giftWrappers;
        Map<Integer, Integer> giftFurnis = manager.giftFurnis;
        Map<Integer, Set<Item>> prizes = manager.prizes;
        Map<Integer, TargetOffer> targetOffers = manager.targetOffers;
        Map<Integer, ClothItem> clothing = manager.clothing;

        giftWrappers.put(1, 11);
        giftFurnis.put(2, 22);
        prizes.put(3, new java.util.HashSet<>());
        targetOffers.put(4, null);
        clothing.put(5, null);
        manager.catalogFeaturedPages.put(6, null);

        assertSame(giftWrappers, manager.giftWrappers);
        assertSame(giftFurnis, manager.giftFurnis);
        assertSame(prizes, manager.prizes);
        assertSame(targetOffers, manager.targetOffers);
        assertSame(clothing, manager.clothing);
        assertEquals(11, manager.giftWrappers.get(1));
        assertEquals(22, manager.giftFurnis.get(2));
        assertTrue(manager.catalogFeaturedPages.containsKey(6));
    }

    @Test
    void firstPartyReadersUseSnapshotsAndReloadsReplaceContents() throws Exception {
        String manager = source("habbohotel/catalog/CatalogManager.java");

        assertTrue(manager.contains("GiftWrappingSnapshot getGiftWrappingSnapshot()"));
        assertTrue(manager.contains("getRecyclerPrizesSnapshot()"));
        assertTrue(manager.contains("getClothingSnapshot()"));
        assertTrue(manager.contains("getTargetOffersSnapshot()"));
        assertTrue(manager.contains("getCatalogFeaturedPagesSnapshot()"));
        assertTrue(manager.contains("return this.catalogFeaturedPages;"),
                "the legacy featured-page getter must remain live");

        assertTrue(manager.contains("replaceContents(this.targetOffers, loadedTargetOffers);"));
        assertTrue(manager.contains("replaceContents(this.prizes, loadedPrizes);"));
        assertTrue(manager.contains("replaceContents(this.clothing, loadedClothing);"));
        assertTrue(manager.contains("replaceGiftWrapping(loadedGiftWrappers, loadedGiftFurnis);"));
        assertTrue(manager.contains("replaceFeaturedPages(loadedFeaturedPages);"));

        assertNoDirectCacheRead("messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java",
                ".giftWrappers", ".giftFurnis");
        assertNoDirectCacheRead("messages/outgoing/catalog/GiftConfigurationComposer.java",
                ".giftWrappers", ".giftFurnis");
        assertNoDirectCacheRead("habbohotel/commands/GiftCommand.java", ".giftFurnis");
        assertNoDirectCacheRead("habbohotel/commands/MassGiftCommand.java", ".giftFurnis");
        assertNoDirectCacheRead("habbohotel/commands/RoomGiftCommand.java", ".giftFurnis");
        assertNoDirectCacheRead("messages/rcon/SendGift.java", ".giftFurnis");
        assertNoDirectCacheRead("messages/outgoing/catalog/RecyclerLogicComposer.java", ".prizes");
        assertNoDirectCacheRead("messages/outgoing/users/UserClothesComposer.java", ".clothing");
        assertNoDirectCacheRead("habbohotel/users/Habbo.java", ".clothing");
        assertNoDirectCacheRead("habbohotel/commands/PromoteTargetOfferCommand.java", ".targetOffers");

        assertFalse(source("messages/outgoing/catalog/CatalogPageComposer.java")
                .contains("getCatalogFeaturedPages()"));
        assertFalse(source("habbohotel/catalog/layouts/FrontPageFeaturedLayout.java")
                .contains("getCatalogFeaturedPages()"));
    }

    @Test
    void snapshotsAreIndependentAndCacheReplacementPreservesIdentity() throws Exception {
        CatalogManager manager = createManager();
        Map<Integer, Integer> giftWrappers = manager.giftWrappers;
        Map<Integer, Integer> giftFurnis = manager.giftFurnis;
        Map<Integer, Set<Item>> prizes = manager.prizes;
        Map<Integer, TargetOffer> targetOffers = manager.targetOffers;
        Map<Integer, ClothItem> clothing = manager.clothing;
        var featuredPages = manager.catalogFeaturedPages;

        manager.giftWrappers.put(1, 11);
        manager.giftFurnis.put(2, 22);
        manager.prizes.put(3, new HashSet<>());
        manager.targetOffers.put(4, null);
        manager.clothing.put(5, null);
        manager.catalogFeaturedPages.put(6, null);

        CatalogManager.GiftWrappingSnapshot giftSnapshot = manager.getGiftWrappingSnapshot();
        giftSnapshot.wrappers().clear();
        giftSnapshot.furniture().clear();
        manager.getRecyclerPrizesSnapshot().get(3).add(null);
        manager.getTargetOffersSnapshot().clear();
        manager.getClothingSnapshot().clear();
        manager.getCatalogFeaturedPagesSnapshot().clear();

        assertEquals(11, manager.giftWrappers.get(1));
        assertEquals(22, manager.giftFurnis.get(2));
        assertFalse(manager.prizes.get(3).contains(null));
        assertTrue(manager.targetOffers.containsKey(4));
        assertTrue(manager.clothing.containsKey(5));
        assertTrue(manager.catalogFeaturedPages.containsKey(6));

        manager.replaceGiftWrapping(Map.of(7, 77), Map.of(8, 88));
        CatalogManager.replaceContents(manager.prizes, Map.of(9, new HashSet<>()));
        Map<Integer, TargetOffer> replacementOffers = new HashMap<>();
        replacementOffers.put(10, null);
        CatalogManager.replaceContents(manager.targetOffers, replacementOffers);
        Map<Integer, ClothItem> replacementClothing = new HashMap<>();
        replacementClothing.put(11, null);
        CatalogManager.replaceContents(manager.clothing, replacementClothing);
        var replacementFeaturedPages = new Int2ObjectOpenHashMap<CatalogFeaturedPage>();
        replacementFeaturedPages.put(12, null);
        manager.replaceFeaturedPages(replacementFeaturedPages);

        assertSame(giftWrappers, manager.giftWrappers);
        assertSame(giftFurnis, manager.giftFurnis);
        assertSame(prizes, manager.prizes);
        assertSame(targetOffers, manager.targetOffers);
        assertSame(clothing, manager.clothing);
        assertSame(featuredPages, manager.catalogFeaturedPages);
        assertEquals(Map.of(7, 77), manager.giftWrappers);
        assertEquals(Map.of(8, 88), manager.giftFurnis);
        assertTrue(manager.prizes.containsKey(9));
        assertTrue(manager.targetOffers.containsKey(10));
        assertTrue(manager.clothing.containsKey(11));
        assertTrue(manager.catalogFeaturedPages.containsKey(12));
    }

    @Test
    void concurrentReloadReplacementAndSnapshotIterationRemainConsistent() throws Exception {
        CatalogManager manager = createManager();
        Map<Integer, Integer> giftWrappers = manager.giftWrappers;
        Map<Integer, Integer> giftFurnis = manager.giftFurnis;
        Map<Integer, Set<Item>> prizes = manager.prizes;
        Map<Integer, TargetOffer> targetOffers = manager.targetOffers;
        Map<Integer, ClothItem> clothing = manager.clothing;
        var featuredPages = manager.catalogFeaturedPages;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> reloads = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1_000; index++) {
                    manager.replaceGiftWrapping(Map.of(index, index), Map.of(index, index));
                    CatalogManager.replaceContents(manager.prizes, Map.of(index, new HashSet<>()));

                    Map<Integer, TargetOffer> offers = new HashMap<>();
                    offers.put(index, null);
                    CatalogManager.replaceContents(manager.targetOffers, offers);

                    Map<Integer, ClothItem> clothes = new HashMap<>();
                    clothes.put(index, null);
                    CatalogManager.replaceContents(manager.clothing, clothes);

                    var featured = new Int2ObjectOpenHashMap<CatalogFeaturedPage>();
                    featured.put(index, null);
                    manager.replaceFeaturedPages(featured);
                }
                return null;
            });
            Future<?> readers = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1_000; index++) {
                    CatalogManager.GiftWrappingSnapshot giftWrapping = manager.getGiftWrappingSnapshot();
                    assertEquals(giftWrapping.wrappers().keySet(), giftWrapping.furniture().keySet());
                    manager.getRecyclerPrizesSnapshot().values().forEach(Set::size);
                    manager.getTargetOffersSnapshot().values().forEach(value -> {
                    });
                    manager.getClothingSnapshot().values().forEach(value -> {
                    });
                    manager.getCatalogFeaturedPagesSnapshot().forEach(value -> {
                    });
                }
                return null;
            });

            start.countDown();
            reloads.get(5, TimeUnit.SECONDS);
            readers.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertSame(giftWrappers, manager.giftWrappers);
        assertSame(giftFurnis, manager.giftFurnis);
        assertSame(prizes, manager.prizes);
        assertSame(targetOffers, manager.targetOffers);
        assertSame(clothing, manager.clothing);
        assertSame(featuredPages, manager.catalogFeaturedPages);
    }

    private static void assertPublicFinalField(String name) throws Exception {
        Field field = CatalogManager.class.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    private static void assertNoDirectCacheRead(String relativePath, String... fragments) throws Exception {
        String source = source(relativePath);
        for (String fragment : fragments) {
            assertFalse(source.contains(fragment), relativePath + " reads " + fragment + " directly");
        }
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + relativePath));
    }

    private CatalogManager createManager() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        try {
            configField.set(null, new ConfigurationManager(
                    Path.of("..", "config example", "config.ini.example").toString()
            ));
            return new CatalogManager(false);
        } finally {
            configField.set(null, previousConfig);
        }
    }
}
