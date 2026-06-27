package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ChestStorage} — the pure contents model behind the Phase-2 wired chests.
 */
class ChestStorageTest {

    @Test
    void addMergesSameKindAndType() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_FURNI, 100, 3);
        chest.add(ChestStorage.KIND_FURNI, 100, 2);
        chest.add(ChestStorage.KIND_FURNI, 200, 1);

        assertEquals(5, chest.count(ChestStorage.KIND_FURNI, 100));
        assertEquals(1, chest.count(ChestStorage.KIND_FURNI, 200));
        assertEquals(6, chest.total(ChestStorage.KIND_FURNI));
        assertEquals(2, chest.entries().size(), "same kind+type must merge into one entry");
    }

    @Test
    void addIgnoresNonPositiveQuantity() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_CURRENCY, 0, 0);
        chest.add(ChestStorage.KIND_CURRENCY, 0, -5);
        assertTrue(chest.isEmpty());
    }

    @Test
    void takeRemovesUpToAvailableAndReportsActual() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_CURRENCY, 0, 100);

        assertEquals(40, chest.take(ChestStorage.KIND_CURRENCY, 0, 40));
        assertEquals(60, chest.count(ChestStorage.KIND_CURRENCY, 0));

        // over-request is capped and drains the entry
        assertEquals(60, chest.take(ChestStorage.KIND_CURRENCY, 0, 999));
        assertEquals(0, chest.count(ChestStorage.KIND_CURRENCY, 0));
        assertTrue(chest.isEmpty(), "drained entries are dropped");
    }

    @Test
    void takeFromMissingTypeReturnsZero() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_FURNI, 100, 2);
        assertEquals(0, chest.take(ChestStorage.KIND_FURNI, 999, 1));
        assertEquals(0, chest.take(ChestStorage.KIND_FURNI, 100, 0));
        assertEquals(2, chest.total(ChestStorage.KIND_FURNI));
    }

    @Test
    void hasReflectsAvailableQuantity() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_FURNI, 100, 3);
        assertTrue(chest.has(ChestStorage.KIND_FURNI, 100, 3));
        assertFalse(chest.has(ChestStorage.KIND_FURNI, 100, 4));
        assertFalse(chest.has(ChestStorage.KIND_FURNI, 200, 1));
    }

    @Test
    void distinctTypesInInsertionOrderSkippingEmpty() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_FURNI, 100, 1);
        chest.add(ChestStorage.KIND_FURNI, 200, 1);
        chest.add(ChestStorage.KIND_CURRENCY, 0, 50);
        chest.take(ChestStorage.KIND_FURNI, 100, 1); // drains type 100

        assertEquals(java.util.List.of(200), chest.distinctTypes(ChestStorage.KIND_FURNI));
        assertEquals(java.util.List.of(0), chest.distinctTypes(ChestStorage.KIND_CURRENCY));
    }

    @Test
    void jsonRoundTripPreservesContents() {
        ChestStorage chest = new ChestStorage();
        chest.add(ChestStorage.KIND_CURRENCY, 0, 250);
        chest.add(ChestStorage.KIND_FURNI, 1234, 5);
        chest.add(ChestStorage.KIND_FURNI, 5678, 2);

        ChestStorage restored = ChestStorage.fromJson(chest.toJson());
        assertEquals(250, restored.count(ChestStorage.KIND_CURRENCY, 0));
        assertEquals(5, restored.count(ChestStorage.KIND_FURNI, 1234));
        assertEquals(2, restored.count(ChestStorage.KIND_FURNI, 5678));
        assertEquals(3, restored.entries().size());
    }

    @Test
    void fromJsonToleratesNullBlankAndMalformed() {
        assertTrue(ChestStorage.fromJson(null).isEmpty());
        assertTrue(ChestStorage.fromJson("").isEmpty());
        assertTrue(ChestStorage.fromJson("not json").isEmpty());
        assertTrue(ChestStorage.fromJson("{").isEmpty());
        assertTrue(ChestStorage.fromJson("{\"entries\":null}").isEmpty());
    }
}
