package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore.Release;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore.ReleaseStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deleting a chest gives what it holds back to its owner: the furni as themselves, the coins as
 * credits exactly once, and nothing is ever dropped for want of an owner.
 */
class ChestReleaseTest {
    private static final int OWNER = 7;
    private static final int CHEST = 100;

    private InMemoryChestStore store;
    private ChestStore previous;

    @BeforeEach
    void useMemoryStore() {
        this.store = new InMemoryChestStore();
        this.previous = ChestStores.use(this.store);
    }

    @AfterEach
    void restoreStore() {
        ChestStores.use(this.previous);
    }

    private void chestHolding(int chestId, int coins, ChestStore.StoredRow... furni) {
        this.store.createChest(chestId, ChestStorage.KIND_FURNI, coins, 2, "{}");
        for (ChestStore.StoredRow row : furni) this.store.storeItem(chestId, row);
    }

    private static ChestStore.StoredRow limitedGroupFurni(int itemId) {
        return new ChestStore.StoredRow(itemId, 4100, "3", "", "12:500", 33, 1_900_000_000, false, itemId);
    }

    private static ChestStore.StoredRow plainFurni(int itemId) {
        return new ChestStore.StoredRow(itemId, 4200, "0", "", "0:0", 0, 0, false, itemId);
    }

    @Test
    void releaseGivesTheFurniBackAsThemselvesAndRefundsTheCoins() {
        this.chestHolding(CHEST, 250, limitedGroupFurni(501), plainFurni(502));

        Release release = ChestRelease.releaseToOwner(CHEST, OWNER);

        InventoryRowCheck.assertRow(this.store.inventoryItem(501), OWNER, 4100, "3", "12:500", 33, 1_900_000_000);
        InventoryRowCheck.assertRow(this.store.inventoryItem(502), OWNER, 4200, "0", "0:0", 0, 0);
        assertAll(
                () -> assertEquals(ReleaseStatus.RELEASED, release.status()),
                () -> assertEquals(OWNER, release.ownerId()),
                () -> assertEquals(Map.of(501, 501, 502, 502), release.furni()),
                () -> assertEquals(250, release.coins()),
                () -> assertEquals(250, release.creditsAfter()),
                () -> assertEquals(250, this.store.creditsOf(OWNER)),
                () -> assertEquals(0, this.store.storedCount(CHEST)),
                () -> assertNull(this.store.loadChest(CHEST), "the chest's rows are gone"),
                () -> assertEquals(Map.of(ChestRelease.operationId(CHEST), 250), this.store.ledger));
    }

    @Test
    void releasingTwicePaysOnce() {
        this.chestHolding(CHEST, 400, plainFurni(601));

        Release first = ChestRelease.releaseToOwner(CHEST, OWNER);
        Release second = ChestRelease.releaseToOwner(CHEST, OWNER);

        assertEquals(ReleaseStatus.RELEASED, first.status());
        assertEquals(ReleaseStatus.NO_CHEST, second.status());
        assertEquals(400, this.store.creditsOf(OWNER));
        assertEquals(1, this.store.inventoryCount(OWNER));

        // Even if the same chest row came back (a replayed backup, a crash between two writes), the
        // ledger operation id stops a second payment.
        this.store.createChest(CHEST, ChestStorage.KIND_FURNI, 400, 0, "{}");
        Release replay = this.store.releaseChest(CHEST, OWNER);
        assertEquals(ReleaseStatus.RELEASED, replay.status());
        assertEquals(0, replay.coins());
        assertEquals(400, this.store.creditsOf(OWNER));
        assertEquals(1, this.store.ledger.size());
    }

    @Test
    void anEmptyChestChangesNothingForItsOwner() {
        this.store.createChest(CHEST, ChestStorage.KIND_CURRENCY, 0, 0, "{}");
        this.store.giveInventoryItem(900, OWNER, 1);

        Release release = ChestRelease.releaseToOwner(CHEST, OWNER);

        assertEquals(ReleaseStatus.EMPTY, release.status());
        assertEquals(0, release.coins());
        assertTrue(release.furni().isEmpty());
        assertEquals(0, this.store.creditsOf(OWNER));
        assertTrue(this.store.ledger.isEmpty());
        assertEquals(1, this.store.inventoryCount(OWNER));
    }

    @Test
    void furniThatIsNoChestIsNeverReleased() {
        this.chestHolding(CHEST, 10, plainFurni(701));

        Release release = ChestRelease.releaseToOwner(55, OWNER);
        // A furni object that is no chest is not even looked up, whatever its id.
        Release object = ChestRelease.releaseToOwner(item(CHEST, OWNER, 0));
        List<Release> batch = ChestRelease.releaseAll(List.of(item(55, OWNER, 0), item(CHEST, OWNER, 0)));

        assertEquals(ReleaseStatus.NO_CHEST, release.status());
        assertEquals(ReleaseStatus.NO_CHEST, object.status());
        assertTrue(batch.isEmpty());
        assertTrue(this.store.releaseCalls().isEmpty(), "nothing but chests reaches the store's release");
        assertNotNull(this.store.loadChest(CHEST), "an unrelated chest is untouched");
    }

    @Test
    void aBatchReleasesOnlyItsChestsWithTheirOwnOwners() {
        this.chestHolding(CHEST, 30, plainFurni(801));
        this.chestHolding(CHEST + 1, 0, plainFurni(802));

        List<Release> releases = ChestRelease.releaseAll(
                List.of(chestItem(CHEST, OWNER), chestItem(CHEST + 1, 8), item(55, OWNER, 0), item(CHEST, OWNER, 0)));

        assertEquals(2, releases.size());
        assertEquals(List.of(CHEST, CHEST + 1), this.store.releaseCalls());
        assertEquals(OWNER, this.store.inventoryItem(801).ownerId());
        assertEquals(8, this.store.inventoryItem(802).ownerId());
        assertEquals(30, this.store.creditsOf(OWNER));
    }

    @Test
    void theChestItemsOwnerIsUsedWhenTheCallerDoesNotKnowIt() {
        this.chestHolding(CHEST, 0, plainFurni(901));
        this.store.giveInventoryItem(CHEST, OWNER, 3000);

        Release release = ChestRelease.releaseToOwner(CHEST, 0);

        assertEquals(ReleaseStatus.RELEASED, release.status());
        assertEquals(OWNER, this.store.inventoryItem(901).ownerId());
    }

    @Test
    void aChestWithoutAKnownOwnerKeepsItsRows() {
        this.store.unknownUsers.add(OWNER);
        this.chestHolding(CHEST, 75, plainFurni(1001));

        Release release = ChestRelease.releaseToOwner(CHEST, OWNER);

        assertEquals(ReleaseStatus.OWNER_UNKNOWN, release.status());
        assertFalse(release.rowsRemoved());
        assertNotNull(this.store.loadChest(CHEST));
        assertEquals(75, this.store.loadChest(CHEST).coins());
        assertEquals(1, this.store.storedCount(CHEST));
        assertTrue(this.store.ledger.isEmpty());
    }

    @Test
    void theOrphanSweepReleasesWhatItCanAndKeepsTheRest() {
        // 300: item gone, owner unknown, holds coins -> kept
        this.chestHolding(300, 10);
        // 301: item gone, owner recorded -> released to them
        this.chestHolding(301, 5, plainFurni(3011));
        this.store.chestOwners.put(301, 2);
        // 302: item still placed in a room -> not an orphan at all
        this.chestHolding(302, 20, plainFurni(3021));
        this.store.placedItems.add(302);
        // 303: item gone, owner unknown, holds nothing -> its empty row is cleared
        this.store.createChest(303, ChestStorage.KIND_CURRENCY, 0, 0, "{}");
        // 304: item gone, recorded owner no longer exists, holds furni -> kept
        this.chestHolding(304, 0, plainFurni(3041));
        this.store.chestOwners.put(304, 9);
        this.store.unknownUsers.add(9);

        int cleared = ChestRelease.sweepOrphans();

        assertAll(
                () -> assertEquals(2, cleared),
                () -> assertEquals(10, this.store.loadChest(300).coins(), "unknown owner: coins kept"),
                () -> assertNull(this.store.loadChest(301)),
                () -> assertEquals(2, this.store.inventoryItem(3011).ownerId()),
                () -> assertEquals(5, this.store.creditsOf(2)),
                () -> assertNotNull(this.store.loadChest(302)),
                () -> assertEquals(1, this.store.storedCount(302)),
                () -> assertNull(this.store.loadChest(303)),
                () -> assertNotNull(this.store.loadChest(304), "owner gone: furni kept"),
                () -> assertEquals(1, this.store.storedCount(304)),
                () -> assertFalse(this.store.releaseCalls().contains(302)));
    }

    @Test
    void temporaryFurniAreNeverLookedUp() {
        HabboItem temporary = chestItem(CHEST, OWNER);
        when(temporary.isTemporary()).thenReturn(true);
        this.chestHolding(CHEST, 10);

        Release release = ChestRelease.releaseToOwner(temporary);

        assertEquals(ReleaseStatus.NO_CHEST, release.status());
        assertNotNull(this.store.loadChest(CHEST));
    }

    static HabboItem chestItem(int id, int ownerId) {
        HabboItem chest = mock(InteractionWiredChestFurni.class);
        when(chest.getId()).thenReturn(id);
        when(chest.getUserId()).thenReturn(ownerId);
        return chest;
    }

    static HabboItem item(int id, int ownerId, int roomId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getUserId()).thenReturn(ownerId);
        when(item.getRoomId()).thenReturn(roomId);
        return item;
    }

    private static final class InventoryRowCheck {
        static void assertRow(
                InMemoryChestStore.InventoryRow row,
                int owner,
                int baseItemId,
                String extraData,
                String limitedData,
                int guildId,
                int expires) {
            assertNotNull(row, "the furni is back in an inventory under its own id");
            assertAll(
                    () -> assertEquals(owner, row.ownerId()),
                    () -> assertEquals(baseItemId, row.baseItemId()),
                    () -> assertEquals(extraData, row.extraData()),
                    () -> assertEquals(limitedData, row.limitedData()),
                    () -> assertEquals(guildId, row.guildId()),
                    () -> assertEquals(expires, row.expires()));
        }
    }
}
