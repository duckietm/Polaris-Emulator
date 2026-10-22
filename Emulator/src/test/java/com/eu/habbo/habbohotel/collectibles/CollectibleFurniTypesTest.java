package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.items.Item;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Furni types go to the client as sprite ids while the tables keep {@code items_base} ids. */
class CollectibleFurniTypesTest {
    /** The Witch's Chair of a real hotel: items_base id 1100009244, sprite (furnidata) id 18570. */
    private static final int CHAIR_ID = 1_100_009_244;

    private static final int CHAIR_SPRITE = 18_570;

    private static IntFunction<Item> items() {
        Item chair = mock(Item.class);
        when(chair.getId()).thenReturn(CHAIR_ID);
        when(chair.getSpriteId()).thenReturn(CHAIR_SPRITE);
        Map<Integer, Item> byId = Map.of(CHAIR_ID, chair);
        return byId::get;
    }

    private static Product product(int type, String itemTypeId) {
        return new Product("p", type, itemTypeId, 0, "common", "", List.of(), null, 0, false, -1, 0);
    }

    @Test
    void furniProductsSendTheSpriteIdNotTheItemsBaseId() {
        String chair = Integer.toString(CHAIR_ID);
        assertEquals(
                "18570", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_FLOOR, chair), items()));
        assertEquals("18570", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_WALL, chair), items()));
        assertEquals(
                "18570", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_CLOTHING, chair), items()));
    }

    @Test
    void theStoredProductKeepsTheItemsBaseIdForDelivery() {
        Product stored = product(CollectibleData.TYPE_FLOOR, Integer.toString(CHAIR_ID));
        CollectibleFurniTypes.wireItemTypeId(stored, items());
        assertEquals(Integer.toString(CHAIR_ID), stored.itemTypeId());
    }

    @Test
    void otherProductTypesAreSentAsStored() {
        String chair = Integer.toString(CHAIR_ID);
        assertEquals(chair, CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_EFFECT, chair), items()));
        assertEquals(
                "ACH_1", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_BADGE, "ACH_1"), items()));
        assertEquals(chair, CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_PET, chair), items()));
        assertEquals(
                chair, CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_CHAT_STYLE, chair), items()));
    }

    @Test
    void anUnknownOrUnparsableFurniIsSentAsStored() {
        assertEquals("77", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_FLOOR, "77"), items()));
        assertEquals("77", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_FLOOR, "77"), null));
        assertEquals("x", CollectibleFurniTypes.wireItemTypeId(product(CollectibleData.TYPE_FLOOR, "x"), items()));
        assertEquals(77, CollectibleFurniTypes.spriteId(77, items()));
        assertEquals(CHAIR_SPRITE, CollectibleFurniTypes.spriteId(CHAIR_ID, items()));
    }

    @Test
    void furniProductTypes() {
        assertTrue(CollectibleFurniTypes.isFurniProduct(CollectibleData.TYPE_WALL));
        assertTrue(CollectibleFurniTypes.isFurniProduct(CollectibleData.TYPE_FLOOR));
        assertTrue(CollectibleFurniTypes.isFurniProduct(CollectibleData.TYPE_CLOTHING));
        assertFalse(CollectibleFurniTypes.isFurniProduct(CollectibleData.TYPE_EFFECT));
        assertFalse(CollectibleFurniTypes.isFurniProduct(CollectibleData.TYPE_BADGE));
    }

    @Test
    void clothingWithoutStoredFigureSetsSendsTheFurniCustomParams() {
        Item cap = mock(Item.class);
        when(cap.getCustomParams()).thenReturn("4981, 4982");
        Item plain = mock(Item.class);
        when(plain.getCustomParams()).thenReturn("0");
        Map<Integer, Item> byId = Map.of(10, cap, 11, plain);
        IntFunction<Item> items = byId::get;

        assertEquals(
                List.of(4981, 4982),
                CollectibleFurniTypes.wireFigureSetIds(product(CollectibleData.TYPE_CLOTHING, "10"), items));
        Product stored = new Product(
                "p", CollectibleData.TYPE_CLOTHING, "10", 0, "common", "", List.of(7), null, 0, false, -1, 0);
        assertEquals(List.of(7), CollectibleFurniTypes.wireFigureSetIds(stored, items));
        assertEquals(
                List.of(), CollectibleFurniTypes.wireFigureSetIds(product(CollectibleData.TYPE_CLOTHING, "11"), items));
        assertEquals(
                List.of(), CollectibleFurniTypes.wireFigureSetIds(product(CollectibleData.TYPE_FLOOR, "10"), items));
        assertEquals(
                List.of(), CollectibleFurniTypes.wireFigureSetIds(product(CollectibleData.TYPE_CLOTHING, "x"), items));
        assertEquals(
                List.of(), CollectibleFurniTypes.wireFigureSetIds(product(CollectibleData.TYPE_CLOTHING, "99"), items));
    }
}
