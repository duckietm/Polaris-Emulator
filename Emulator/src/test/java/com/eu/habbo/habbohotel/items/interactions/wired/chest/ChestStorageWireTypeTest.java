package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chest wire carries the client-facing sprite id (furnidata classId), not the emulator's
 * internal base item id — the client resolves icons and names against furnidata and echoes the
 * same id back on withdraw. These tests pin that mapping at the storage level.
 */
class ChestStorageWireTypeTest {

    private static ChestFurniStoredItem storedItem(int baseItemId, int spriteId) {
        ChestFurniStoredItem item = new ChestFurniStoredItem();
        item.baseItemId = baseItemId;
        item.spriteId = spriteId;
        item.extradata = "0";
        return item;
    }

    @Test
    void wireTypeIdPrefersSpriteIdAndFallsBackToBaseItemId() {
        assertEquals(9500, storedItem(1389, 9500).wireTypeId());
        assertEquals(1389, storedItem(1389, 0).wireTypeId());
    }

    @Test
    void withdrawByWireTypeMatchesTheSpriteIdTheClientSaw() {
        ChestStorage storage = new ChestStorage();
        storage.addFurniItem(storedItem(1389, 9500));
        storage.addFurniItem(storedItem(1389, 9500));
        storage.addFurniItem(storedItem(77, 88));

        assertEquals(2, storage.countFurniByWireType(false, 9500, ""));
        // The internal base item id is NOT a valid wire id for this row.
        assertEquals(0, storage.countFurniByWireType(false, 1389, ""));

        List<ChestFurniStoredItem> removed = storage.removeFurniByWireType(false, 9500, "", 5);
        assertEquals(2, removed.size());
        assertEquals(1389, removed.get(0).baseItemId);
        assertEquals(0, storage.countFurniByWireType(false, 9500, ""));

        // Aggregate entries (used by wired conditions) stay keyed by the internal id and shrink too.
        assertEquals(0, storage.count(ChestStorage.KIND_FURNI, 1389));
        assertEquals(1, storage.count(ChestStorage.KIND_FURNI, 77));
    }

    @Test
    void legacyRowsWithoutSpriteIdStillMatchByBaseItemId() {
        ChestStorage storage = new ChestStorage();
        storage.addFurniItem(storedItem(1389, 0));

        assertEquals(1, storage.countFurniByWireType(false, 1389, ""));
        assertEquals(1, storage.removeFurniByWireType(false, 1389, "", 1).size());
    }

    @Test
    void wallItemsMatchOnPosterToo() {
        ChestFurniStoredItem poster = storedItem(500, 4001);
        poster.wallItem = true;
        poster.legacyPosterId = "23";

        ChestStorage storage = new ChestStorage();
        storage.addFurniItem(poster);

        assertEquals(0, storage.countFurniByWireType(true, 4001, "99"));
        assertEquals(1, storage.countFurniByWireType(true, 4001, "23"));
        assertEquals(1, storage.removeFurniByWireType(true, 4001, "23", 1).size());
    }
}
