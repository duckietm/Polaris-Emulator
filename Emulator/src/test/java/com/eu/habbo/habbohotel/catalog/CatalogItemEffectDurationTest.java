package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import org.junit.jupiter.api.Test;

class CatalogItemEffectDurationTest {

    @Test
    void emptyOrUnreadableExtradataSellsTheOneDayDefault() {
        assertEquals(CatalogItem.DEFAULT_EFFECT_DURATION, CatalogItem.effectDuration(null));
        assertEquals(CatalogItem.DEFAULT_EFFECT_DURATION, CatalogItem.effectDuration(" "));
        assertEquals(CatalogItem.DEFAULT_EFFECT_DURATION, CatalogItem.effectDuration("forever"));
    }

    @Test
    void permanentOrZeroSellsAPermanentEffect() {
        assertEquals(0, CatalogItem.effectDuration("permanent"));
        assertEquals(0, CatalogItem.effectDuration("PERMANENT"));
        assertEquals(0, CatalogItem.effectDuration("0"));
        assertEquals(0, CatalogItem.effectDuration("-5"));
    }

    @Test
    void secondsAreUsedAndCappedAtOneYear() {
        assertEquals(3600, CatalogItem.effectDuration("3600"));
        assertEquals(604800, CatalogItem.effectDuration(" 604800 "));
        assertEquals(CatalogItem.MAX_EFFECT_DURATION, CatalogItem.effectDuration("999999999"));
        assertEquals(CatalogItem.DEFAULT_EFFECT_DURATION, CatalogItem.effectDuration("99999999999"));
    }

    @Test
    void effectProductsSendTheEffectIdAsClassId() {
        assertEquals(158, CatalogItem.catalogClassId(item(FurnitureType.EFFECT, 0, 158)));
        assertEquals(42, CatalogItem.catalogClassId(item(FurnitureType.EFFECT, 42, 0)));
        assertEquals(3012, CatalogItem.catalogClassId(item(FurnitureType.FLOOR, 3012, 147)));
    }

    private static Item item(FurnitureType type, int spriteId, int effectId) {
        Item item = mock(Item.class);
        when(item.getType()).thenReturn(type);
        when(item.getSpriteId()).thenReturn(spriteId);
        when(item.getEffectM()).thenReturn(effectId);
        return item;
    }
}
