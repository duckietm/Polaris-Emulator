package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPublicCacheCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void publicCatalogCachesRemainFinalMutableObjects() throws Exception {
        Path configFile = this.tempDir.resolve("config.ini");
        Files.writeString(configFile, "");
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        CatalogManager manager;
        try {
            configField.set(null, new ConfigurationManager(configFile.toString()));
            manager = new CatalogManager(false);
        } finally {
            configField.set(null, previousConfig);
        }

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

    private static void assertPublicFinalField(String name) throws Exception {
        Field field = CatalogManager.class.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }
}
