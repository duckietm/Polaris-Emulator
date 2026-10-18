package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deposits and withdrawals on the AIR chest model under contention: an item is handed out once,
 * coins never go negative or appear from nowhere, and a full chest refuses rather than overflows.
 */
class ChestTransferRaceTest {
    private static final int OWNER = 2;
    private static final int THREADS = 8;

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

    private static Item tradeableFloorFurni() {
        Item base = mock(Item.class);
        when(base.allowTrade()).thenReturn(true);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(base.getSpriteId()).thenReturn(4000);
        when(base.getId()).thenReturn(400);
        return base;
    }

    private List<HabboItem> inventory(int ownerId, int firstId, int count) {
        Item base = tradeableFloorFurni();
        List<HabboItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = firstId + i;
            HabboItem item = mock(HabboItem.class);
            when(item.getId()).thenReturn(id);
            when(item.getBaseItem()).thenReturn(base);
            when(item.getExtradata()).thenReturn("0");
            this.store.giveInventoryItem(id, ownerId, 400);
            items.add(item);
        }
        return items;
    }

    private static InteractionWiredChestFurni furniChest(int id) {
        return new InteractionWiredChestFurni(id, OWNER, mock(Item.class), "", 0, 0);
    }

    private static InteractionWiredChestCurrency coinChest(int id) {
        return new InteractionWiredChestCurrency(id, OWNER, mock(Item.class), "", 0, 0);
    }

    private static void race(int threads, Runnable work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    work.run();
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), () -> "worker failed: " + failures);
    }

    @Test
    void aNewChestGetsItsStoreRowAndStartsEmpty() {
        InteractionWiredChestFurni chest = furniChest(100);

        assertEquals(0, chest.getContents().furniItemCount());
        assertTrue(this.store.chests.containsKey(100));
        assertFalse(chest.getContents().isWiredEnabled(), "a new chest is not upgraded");
        assertEquals(ChestCapacity.FURNI_INITIAL, chest.getContents().getCapacityMax());
    }

    @Test
    void racingWithdrawalsHandEveryItemOutExactlyOnce() throws Exception {
        InteractionWiredChestFurni chest = furniChest(100);
        assertEquals(50, chest.depositFurni(OWNER, this.inventory(OWNER, 1, 50)).size());
        assertEquals(50, this.store.storedCount(100));

        ConcurrentLinkedQueue<Integer> delivered = new ConcurrentLinkedQueue<>();
        AtomicInteger user = new AtomicInteger(10);
        race(THREADS, () -> {
            int me = user.incrementAndGet();
            for (int round = 0; round < 20; round++) {
                Map<ChestFurniStoredItem, Integer> got =
                        chest.withdrawFurni(me, contents -> contents.removeFurniInOrder(1, 3, new Random()));
                delivered.addAll(got.values());
            }
        });

        assertEquals(50, delivered.size());
        assertEquals(50, new HashSet<>(delivered).size(), "no item was handed out twice");
        assertEquals(0, chest.getContents().furniItemCount());
        assertEquals(0, this.store.storedCount(100));
        int inInventories = 0;
        for (int u = 11; u <= 10 + THREADS; u++) inInventories += this.store.inventoryCount(u);
        assertEquals(50, inInventories);
    }

    @Test
    void depositsAndWithdrawalsRacingNeverLoseOrDuplicate() throws Exception {
        InteractionWiredChestFurni chest = furniChest(100);
        List<List<HabboItem>> batches = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) batches.add(this.inventory(OWNER, 1000 + t * 10, 10));

        AtomicInteger next = new AtomicInteger();
        ConcurrentLinkedQueue<Integer> delivered = new ConcurrentLinkedQueue<>();
        race(THREADS, () -> {
            List<HabboItem> mine = batches.get(next.getAndIncrement());
            chest.depositFurni(OWNER, mine);
            delivered.addAll(chest.withdrawFurni(99, contents -> contents.removeFurniInOrder(2, 4, new Random()))
                    .values());
        });

        int total = THREADS * 10;
        assertEquals(new HashSet<>(delivered).size(), delivered.size());
        assertEquals(total, this.store.storedCount(100) + this.store.inventoryCount(99));
        assertEquals(this.store.storedCount(100), chest.getContents().furniItemCount());
    }

    @Test
    void theSameItemCannotGoIntoTwoChests() throws Exception {
        InteractionWiredChestFurni first = furniChest(100);
        InteractionWiredChestFurni second = furniChest(101);
        List<HabboItem> item = this.inventory(OWNER, 7, 1);

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger turn = new AtomicInteger();
        race(2, () -> {
            InteractionWiredChestFurni target = turn.getAndIncrement() == 0 ? first : second;
            accepted.addAndGet(target.depositFurni(OWNER, item).size());
        });

        assertEquals(1, accepted.get());
        assertEquals(
                1, first.getContents().furniItemCount() + second.getContents().furniItemCount());
    }

    @Test
    void aFullChestTakesWhatFitsAndLeavesTheRest() {
        InteractionWiredChestFurni chest = furniChest(100);
        chest.getContents().setCapacity(10);

        List<HabboItem> accepted = chest.depositFurni(OWNER, this.inventory(OWNER, 1, 15));

        assertEquals(10, accepted.size());
        assertEquals(5, this.store.inventoryCount(OWNER), "the rest never left the inventory");
    }

    @Test
    void untradeableFurniAndChestsAreRefused() {
        InteractionWiredChestFurni chest = furniChest(100);
        HabboItem untradeable = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(untradeable.getBaseItem()).thenReturn(base);
        when(untradeable.getId()).thenReturn(5);
        this.store.giveInventoryItem(5, OWNER, 1);

        InteractionWiredChestFurni anotherChest = furniChest(6);
        this.store.giveInventoryItem(6, OWNER, 1);

        assertTrue(chest.depositFurni(OWNER, List.of(untradeable, anotherChest)).isEmpty());
    }

    @Test
    void racingCoinWithdrawalsNeverTakeMoreThanTheChestHolds() throws Exception {
        InteractionWiredChestCurrency chest = coinChest(200);
        assertEquals(1000, chest.addCoins(1000));

        AtomicInteger taken = new AtomicInteger();
        race(THREADS, () -> {
            for (int i = 0; i < 5; i++) taken.addAndGet(chest.takeCoins(70));
        });

        assertEquals(1000, taken.get());
        assertEquals(0, chest.getContents().getCoins());
        assertEquals(0, this.store.chests.get(200).coins());
    }

    @Test
    void coinsStopAtTheCapacity() {
        InteractionWiredChestCurrency chest = coinChest(200);

        assertEquals(ChestCapacity.COINS_INITIAL, chest.addCoins(ChestCapacity.COINS_INITIAL + 500));
        assertEquals(0, chest.addCoins(1));
        assertEquals(ChestCapacity.COINS_INITIAL, this.store.chests.get(200).coins());
    }

    @Test
    void creditFurniAreRedeemedAllOrNothing() {
        InteractionWiredChestCurrency chest = coinChest(200);
        List<HabboItem> credits = this.inventory(OWNER, 1, 2);

        assertFalse(chest.redeemCreditFurni(OWNER, credits, ChestCapacity.COINS_INITIAL + 1), "over capacity");
        assertEquals(2, this.store.inventoryCount(OWNER));

        assertTrue(chest.redeemCreditFurni(OWNER, credits, 100));
        assertEquals(0, this.store.inventoryCount(OWNER));
        assertEquals(100, chest.getContents().getCoins());
        assertFalse(chest.redeemCreditFurni(OWNER, credits, 100), "already redeemed");
        assertEquals(100, chest.getContents().getCoins());
    }

    @Test
    void aWithdrawalTheStoreRefusesLeavesTheChestAsItWas() {
        InteractionWiredChestFurni chest = furniChest(100);
        chest.depositFurni(OWNER, this.inventory(OWNER, 1, 3));

        ChestStore refusing = new InMemoryChestStore() {
            @Override
            public synchronized Map<Integer, Integer> withdrawItems(int chestId, int toUserId, List<Integer> ids) {
                return Map.of();
            }
        };
        ChestStores.use(refusing);

        assertTrue(chest.withdrawFurni(5, ChestStorage::removeAllFurniItems).isEmpty());
        assertEquals(3, chest.getContents().furniItemCount());
        Set<Integer> ids = new HashSet<>();
        chest.getContents().furniItems().forEach(row -> ids.add(row.inventoryId));
        assertEquals(Set.of(1, 2, 3), ids);
    }

    @Test
    void aChestReloadsWhatItHoldsFromTheStore() {
        InteractionWiredChestFurni chest = furniChest(100);
        chest.depositFurni(OWNER, this.inventory(OWNER, 1, 4));
        chest.getContents().setName("Loot");
        chest.persistContents();

        InteractionWiredChestFurni reloaded = furniChest(100);
        assertEquals(4, reloaded.getContents().furniItemCount());
        assertEquals("Loot", reloaded.getContents().getName());
    }

    @Test
    void pickingAChestUpKeepsItsContents() {
        InteractionWiredChestCurrency chest = coinChest(200);
        chest.addCoins(300);

        chest.onPickUp();

        assertEquals(300, chest.getContents().getCoins());
        assertEquals(300, this.store.chests.get(200).coins());
    }
}
