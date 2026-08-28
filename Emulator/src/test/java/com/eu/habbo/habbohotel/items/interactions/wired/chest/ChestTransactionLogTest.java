package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The room-scoped transaction log persists the furni a transaction moved as a flat
 * {@code spriteId:quantity} pair list, and the detail window renders exactly what comes back out.
 * These tests pin that round trip plus the filter guard, which are the parts a malformed row or a
 * hostile request can reach.
 */
class ChestTransactionLogTest {

    private static ChestFurniStoredItem storedItem(int baseItemId, int spriteId) {
        ChestFurniStoredItem item = new ChestFurniStoredItem();
        item.baseItemId = baseItemId;
        item.spriteId = spriteId;
        item.extradata = "0";
        return item;
    }

    @Test
    void encodesMovedFurniAsSpriteIdQuantityPairs() {
        List<ChestFurniStoredItem> moved = List.of(storedItem(1389, 9500), storedItem(1389, 9500), storedItem(77, 88));

        assertEquals("9500:2,88:1", ChestTransactionLog.encodeDetails(moved));
    }

    @Test
    void encodesUnresolvedSpriteIdsUnderTheBaseItemIdTheChestFallsBackTo() {
        assertEquals("1389:1", ChestTransactionLog.encodeDetails(List.of(storedItem(1389, 0))));
    }

    @Test
    void currencyTransactionsCarryNoDetailPayload() {
        assertNull(ChestTransactionLog.encodeDetails(null));
        assertNull(ChestTransactionLog.encodeDetails(new ArrayList<>()));
    }

    @Test
    void decodesBackToTheSameLines() {
        List<ChestTransactionLog.DetailItem> items = ChestTransactionLog.decodeDetails("9500:2,88:1");

        assertEquals(2, items.size());
        assertEquals(9500, items.get(0).spriteId());
        assertEquals(2, items.get(0).quantity());
        assertEquals(88, items.get(1).spriteId());
        assertEquals(1, items.get(1).quantity());
    }

    @Test
    void aMalformedPairDropsOutInsteadOfLosingTheWholeDetail() {
        List<ChestTransactionLog.DetailItem> items = ChestTransactionLog.decodeDetails("9500:2,broken,:5,7:,0:3,88:1");

        assertEquals(2, items.size());
        assertEquals(9500, items.get(0).spriteId());
        assertEquals(88, items.get(1).spriteId());
    }

    @Test
    void anEmptyDetailReadsAsNoItemsRatherThanThrowing() {
        assertTrue(ChestTransactionLog.decodeDetails(null).isEmpty());
        assertTrue(ChestTransactionLog.decodeDetails("").isEmpty());
    }

    @Test
    void onlyTheThreeKnownFiltersSurviveNormalisation() {
        assertEquals(
                ChestTransactionLog.FILTER_ALL, ChestTransactionLog.normalizeFilter(ChestTransactionLog.FILTER_ALL));
        assertEquals(
                ChestTransactionLog.FILTER_CURRENCY,
                ChestTransactionLog.normalizeFilter(ChestTransactionLog.FILTER_CURRENCY));
        assertEquals(
                ChestTransactionLog.FILTER_FURNI,
                ChestTransactionLog.normalizeFilter(ChestTransactionLog.FILTER_FURNI));
        assertEquals(ChestTransactionLog.FILTER_ALL, ChestTransactionLog.normalizeFilter(-4));
        assertEquals(ChestTransactionLog.FILTER_ALL, ChestTransactionLog.normalizeFilter(99));
    }

    @Test
    void theLockSurvivesAWiredDataRoundTrip() {
        ChestStorage storage = new ChestStorage();
        storage.setLocked(true);

        ChestStorage reloaded = ChestStorage.fromJson(storage.toJson());

        assertTrue(reloaded.isLocked());
    }

    @Test
    void aChestPersistedBeforeTheLockExistedReadsAsUnlocked() {
        ChestStorage reloaded = ChestStorage.fromJson("{\"capacityMax\":5000,\"accessOpen\":true}");

        assertEquals(false, reloaded.isLocked());
    }
}
