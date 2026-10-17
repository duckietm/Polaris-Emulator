package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ChestStore} over plain maps with the same guards as the database store: an inventory item
 * only moves while its owner still has it, a stored item only leaves the chest it is in, and coins
 * never go below zero. Tests use it to check the chest rules, races included, without a database.
 */
public class InMemoryChestStore implements ChestStore {
    /** An item in {@code items}: who has it (room 0) and what it is. */
    record InventoryRow(int ownerId, int baseItemId, String extraData) {}

    final Map<Integer, ChestRow> chests = new HashMap<>();
    final Map<Integer, InventoryRow> items = new HashMap<>();
    final Map<Integer, StoredRow> stored = new LinkedHashMap<>();
    final Map<Integer, Integer> storedChest = new HashMap<>();
    int creditsRedeemed;

    synchronized void giveInventoryItem(int itemId, int ownerId, int baseItemId) {
        this.items.put(itemId, new InventoryRow(ownerId, baseItemId, "0"));
    }

    synchronized int inventoryCount(int ownerId) {
        return (int) this.items.values().stream()
                .filter(row -> row.ownerId() == ownerId)
                .count();
    }

    synchronized int storedCount(int chestId) {
        return (int)
                this.storedChest.values().stream().filter(id -> id == chestId).count();
    }

    @Override
    public synchronized ChestRow loadChest(int chestId) {
        return this.chests.get(chestId);
    }

    @Override
    public synchronized void createChest(int chestId, int kind, int coins, int capacityLevel, String settings) {
        this.chests.putIfAbsent(chestId, new ChestRow(chestId, kind, coins, capacityLevel, settings));
    }

    @Override
    public synchronized void saveSettings(int chestId, int capacityLevel, String settings) {
        ChestRow row = this.chests.get(chestId);
        if (row != null) {
            this.chests.put(chestId, new ChestRow(chestId, row.kind(), row.coins(), capacityLevel, settings));
        }
    }

    @Override
    public synchronized boolean addCoins(int chestId, int delta) {
        ChestRow row = this.chests.get(chestId);
        if (row == null || row.coins() + (long) delta < 0) return false;
        this.chests.put(
                chestId, new ChestRow(chestId, row.kind(), row.coins() + delta, row.capacityLevel(), row.settings()));
        return true;
    }

    @Override
    public synchronized List<StoredRow> loadItems(int chestId) {
        List<StoredRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, StoredRow> entry : this.stored.entrySet()) {
            if (this.storedChest.get(entry.getKey()) == chestId) rows.add(entry.getValue());
        }
        rows.sort(Comparator.comparingLong(StoredRow::sequence).thenComparingInt(StoredRow::itemId));
        return rows;
    }

    @Override
    public synchronized List<Integer> depositItems(int chestId, int fromUserId, List<Integer> itemIds, long sequence) {
        List<Integer> moved = new ArrayList<>();
        for (int itemId : itemIds) {
            InventoryRow row = this.items.get(itemId);
            if (row == null || row.ownerId() != fromUserId) continue;
            this.items.remove(itemId);
            this.stored.put(
                    itemId, new StoredRow(itemId, row.baseItemId(), row.extraData(), "", "0:0", 0, 0, false, sequence));
            this.storedChest.put(itemId, chestId);
            moved.add(itemId);
        }
        return moved;
    }

    @Override
    public synchronized Map<Integer, Integer> withdrawItems(int chestId, int toUserId, List<Integer> itemIds) {
        Map<Integer, Integer> moved = new LinkedHashMap<>();
        for (int itemId : itemIds) {
            Integer in = this.storedChest.get(itemId);
            if (in == null || in != chestId) continue;
            StoredRow row = this.stored.remove(itemId);
            this.storedChest.remove(itemId);
            this.items.put(itemId, new InventoryRow(toUserId, row.baseItemId(), row.extraData()));
            moved.put(itemId, itemId);
        }
        return moved;
    }

    @Override
    public synchronized boolean redeemCreditFurni(int chestId, int fromUserId, List<Integer> itemIds, int coins) {
        for (int itemId : itemIds) {
            InventoryRow row = this.items.get(itemId);
            if (row == null || row.ownerId() != fromUserId) return false;
        }
        if (!this.chests.containsKey(chestId)) return false;
        itemIds.forEach(this.items::remove);
        this.creditsRedeemed += coins;
        return this.addCoins(chestId, coins);
    }
}
