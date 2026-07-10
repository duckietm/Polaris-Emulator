package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-abuse / packet-injection guards on the chest storage model. These pin the invariants the
 * packet handlers rely on: a withdraw can never take out more value than is stored (no duplication),
 * deposits can never exceed capacity, and concurrent access from multiple threads (player packets on
 * per-channel Netty threads + wired effects on the room thread) can neither corrupt the state nor
 * duplicate items.
 */
class ChestStorageSecurityTest {

    private static ChestFurniStoredItem furni(int baseItemId, int spriteId) {
        ChestFurniStoredItem item = new ChestFurniStoredItem();
        item.baseItemId = baseItemId;
        item.spriteId = spriteId;
        item.extradata = "0";
        return item;
    }

    @Test
    void withdrawCurrencyNeverExceedsStoredBalance() {
        ChestStorage storage = new ChestStorage();
        storage.add(ChestStorage.KIND_CURRENCY, -1, 100);

        // A spoofed huge amount can only take what's actually there.
        assertEquals(100, storage.withdrawCurrency(-1, Integer.MAX_VALUE));
        assertEquals(0, storage.count(ChestStorage.KIND_CURRENCY, -1));
        // Second withdraw of an emptied chest yields nothing (no negative, no free credits).
        assertEquals(0, storage.withdrawCurrency(-1, 50));
    }

    @Test
    void withdrawAllUsesNegativeAmountSentinel() {
        ChestStorage storage = new ChestStorage();
        storage.add(ChestStorage.KIND_CURRENCY, 5, 42);

        assertEquals(42, storage.withdrawCurrency(5, -1));
        assertEquals(0, storage.total(ChestStorage.KIND_CURRENCY));
    }

    @Test
    void depositCurrencyIsCappedAtCapacity() {
        ChestStorage storage = new ChestStorage();
        // Default capacity is 5000.
        assertEquals(ChestStorage.DEFAULT_CAPACITY, storage.getCapacityMax());

        assertEquals(5000, storage.depositCurrency(-1, 999_999));
        // Full: further deposits are rejected outright, so the caller debits nothing.
        assertEquals(0, storage.depositCurrency(-1, 1));
        assertEquals(5000, storage.total(ChestStorage.KIND_CURRENCY));
    }

    @Test
    void tryDepositFurniRefusesWhenFull() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacityMax(ChestStorage.DEFAULT_CAPACITY);

        // Fill to capacity.
        for (int i = 0; i < ChestStorage.DEFAULT_CAPACITY; i++) {
            assertTrue(storage.tryDepositFurni(furni(100, 200)));
        }

        assertEquals(ChestStorage.DEFAULT_CAPACITY, storage.furniItemCount());
        // One more must be refused (no silent overflow).
        assertTrue(!storage.tryDepositFurni(furni(100, 200)));
        assertEquals(ChestStorage.DEFAULT_CAPACITY, storage.furniItemCount());
    }

    @Test
    void concurrentWithdrawNeverDuplicatesFurni() throws InterruptedException {
        ChestStorage storage = new ChestStorage();
        int stored = 500;
        for (int i = 0; i < stored; i++) {
            storage.addFurniItem(furni(100, 200));
        }

        // Many threads (simulating multiple players + wired all racing the same chest) try to take
        // everything at once. The sum of what they each remove must equal exactly what was stored —
        // never more (which would be a dupe), and each inventory id handed out exactly once.
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<ChestFurniStoredItem> withdrawn = new ConcurrentLinkedQueue<>();
        AtomicInteger done = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    List<ChestFurniStoredItem> got;
                    do {
                        got = storage.removeFurniByWireType(false, 200, "", 7);
                        withdrawn.addAll(got);
                    } while (!got.isEmpty());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "withdraw threads did not finish");
        assertEquals(threads, done.get());

        assertEquals(stored, withdrawn.size(), "total withdrawn must equal total stored (no duplication)");
        assertEquals(0, storage.furniItemCount());
        long distinctIds = withdrawn.stream().map(i -> i.inventoryId).distinct().count();
        assertEquals(stored, distinctIds, "each stored item must be handed out exactly once");
    }

    @Test
    void concurrentDepositNeverExceedsCapacity() throws InterruptedException {
        ChestStorage storage = new ChestStorage();
        storage.setCapacityMax(ChestStorage.DEFAULT_CAPACITY); // 5000

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        // Every thread tries to deposit 2000 items; total attempted (16000) far exceeds capacity.
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        if (storage.tryDepositFurni(furni(100, 200))) {
                            accepted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "deposit threads did not finish");

        assertEquals(ChestStorage.DEFAULT_CAPACITY, accepted.get(), "accepted deposits must equal capacity");
        assertEquals(ChestStorage.DEFAULT_CAPACITY, storage.furniItemCount(), "chest must never exceed capacity");
    }
}
