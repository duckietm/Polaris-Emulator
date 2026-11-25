package polaris.hotel.items.interactions.wired.chest;

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
    void aWallItemKeepsItsKindThroughTheDetail() {
        ChestFurniStoredItem poster = storedItem(500, 4001);
        poster.wallItem = true;
        ChestFurniStoredItem chair = storedItem(77, 4001);

        String encoded = ChestTransactionLog.encodeDetails(List.of(poster, poster, chair));
        assertEquals("-4001:2,4001:1", encoded, "the same sprite id as wall and floor furni are two lines");

        List<ChestTransactionLog.DetailItem> items = ChestTransactionLog.decodeDetails(encoded);
        assertEquals(2, items.size());
        assertEquals(-4001, items.get(0).spriteId(), "a wall item reads back negated");
        assertEquals(2, items.get(0).quantity());
        assertEquals(4001, items.get(1).spriteId());
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

    private static ChestTransactionLog.StoredRow row(
            int id, int chestId, int kind, int type, int source, int amount, String details, int method) {
        boolean withdraw = type == ChestTransactionLog.TYPE_WITHDRAW;
        return new ChestTransactionLog.StoredRow(
                id,
                chestId,
                kind,
                type,
                source,
                7,
                "Frank",
                withdraw ? amount : 0,
                withdraw ? 0 : amount,
                details,
                1000 + id,
                method,
                method < 0 ? "" : "55, x2");
    }

    @Test
    void aContractTradeOverSeveralChestsIsOneTransaction() {
        ChestTransactionLog.TransactionDetails details = ChestTransactionLog.combine(
                9,
                List.of(
                        row(40, 100, ChestStorage.KIND_FURNI, ChestTransactionLog.TYPE_DEPOSIT, 0, 2, "88:2", 4),
                        row(41, 101, ChestStorage.KIND_CURRENCY, ChestTransactionLog.TYPE_DEPOSIT, 0, 30, null, 4),
                        row(42, 100, ChestStorage.KIND_FURNI, ChestTransactionLog.TYPE_WITHDRAW, 1, 3, "-12:1,9:2", 4),
                        row(43, 102, ChestStorage.KIND_CURRENCY, ChestTransactionLog.TYPE_WITHDRAW, 1, 5, null, 4)));

        ChestTransactionLog.Transaction transaction = details.transaction();
        assertEquals(40, transaction.id(), "the first row names the transaction");
        assertEquals(9, transaction.roomId());
        assertEquals(ChestTransactionLog.METHOD_CONTRACT_TRADE, transaction.method());
        assertEquals("55, x2", transaction.definitionInfo());
        assertEquals("Frank", transaction.userName());
        assertEquals(1043, transaction.timestamp());
        assertEquals(3, transaction.chestCount());
        assertEquals(3, transaction.withdrawnFurni());
        assertEquals(2, transaction.depositedFurni());
        assertEquals(5, transaction.withdrawnCoins());
        assertEquals(30, transaction.depositedCoins());
        assertEquals(List.of(100, 101, 102), details.chestIds());
        assertEquals(
                List.of(new ChestTransactionLog.DetailItem(-12, 1), new ChestTransactionLog.DetailItem(9, 2)),
                details.withdrawn());
        assertEquals(List.of(new ChestTransactionLog.DetailItem(88, 2)), details.deposited());
        assertEquals(false, details.incomplete());
    }

    @Test
    void aRowFromBeforeTheMethodsReadsItsMethodFromWhoMovedIt() {
        assertEquals(
                ChestTransactionLog.METHOD_MANUAL, ChestTransactionLog.methodOf(-1, ChestTransactionLog.SOURCE_USER));
        assertEquals(
                ChestTransactionLog.METHOD_WIRED, ChestTransactionLog.methodOf(-1, ChestTransactionLog.SOURCE_WIRED));
        assertEquals(
                ChestTransactionLog.METHOD_CONTRACT_REWARD,
                ChestTransactionLog.methodOf(
                        ChestTransactionLog.METHOD_CONTRACT_REWARD, ChestTransactionLog.SOURCE_WIRED));
    }

    @Test
    void furniTheLinesDoNotNameMarkTheDetailsIncomplete() {
        ChestTransactionLog.TransactionDetails details = ChestTransactionLog.combine(
                9, List.of(row(40, 100, ChestStorage.KIND_FURNI, ChestTransactionLog.TYPE_WITHDRAW, 0, 4, "88:1", -1)));

        assertTrue(details.incomplete());
        assertEquals(ChestTransactionLog.METHOD_MANUAL, details.transaction().method());
    }

    @Test
    void theExtraInfoNamesTheBoxOrTheContract() {
        assertEquals("812, 25", ChestTransactionLog.wiredInfo(812, 25, false));
        assertEquals("812, all", ChestTransactionLog.wiredInfo(812, 25, true));
        assertEquals("55, x3", ChestTransactionLog.contractInfo(55, 3));
        assertEquals("55, x1", ChestTransactionLog.contractInfo(55, 0));
        assertEquals(255, ChestTransactionLog.clampInfo("x".repeat(400)).length());
    }
}
