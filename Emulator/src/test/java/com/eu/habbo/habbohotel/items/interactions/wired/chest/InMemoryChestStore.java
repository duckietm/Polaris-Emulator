package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ChestStore} over plain maps with the same guards as the database store: an inventory item
 * only moves while its owner still has it, a stored item only leaves the chest it is in, and coins
 * never go below zero. Tests use it to check the chest rules, races included, without a database.
 */
public class InMemoryChestStore implements ChestStore {
    /** An item in {@code items}: who has it (room 0) and what it is. */
    public record InventoryRow(
            int ownerId, int baseItemId, String extraData, String limitedData, int guildId, int expires) {
        InventoryRow(int ownerId, int baseItemId, String extraData) {
            this(ownerId, baseItemId, extraData, "0:0", 0, 0);
        }
    }

    final Map<Integer, ChestRow> chests = new HashMap<>();
    final Map<Integer, InventoryRow> items = new HashMap<>();
    final Map<Integer, StoredRow> stored = new LinkedHashMap<>();
    final Map<Integer, Integer> storedChest = new HashMap<>();
    int creditsRedeemed;

    /** Placed items that exist in {@code items} (room furni), next to the inventory rows above. */
    final Set<Integer> placedItems = new HashSet<>();
    /** The owner recorded on each chest row ({@code wired_chests.owner_id}). */
    final Map<Integer, Integer> chestOwners = new HashMap<>();
    /** Users that do not exist. Every other positive id does. */
    final Set<Integer> unknownUsers = new HashSet<>();
    /** The economy ledger: operation id to amount, and every user's credits. */
    final Map<String, Integer> ledger = new HashMap<>();

    final Map<Integer, Integer> credits = new HashMap<>();
    /** Every chest {@link #releaseChest} was asked about, in order. */
    final List<Integer> releaseCalls = new ArrayList<>();

    /** Put a furni into a chest as it would be stored. */
    public synchronized void storeItem(int chestId, StoredRow row) {
        this.stored.put(row.itemId(), row);
        this.storedChest.put(row.itemId(), chestId);
    }

    public synchronized InventoryRow inventoryItem(int itemId) {
        return this.items.get(itemId);
    }

    public synchronized int creditsOf(int userId) {
        return this.credits.getOrDefault(userId, 0);
    }

    public synchronized List<Integer> releaseCalls() {
        return List.copyOf(this.releaseCalls);
    }

    public synchronized void giveInventoryItem(int itemId, int ownerId, int baseItemId) {
        this.items.put(itemId, new InventoryRow(ownerId, baseItemId, "0"));
    }

    public synchronized int inventoryCount(int ownerId) {
        return (int) this.items.values().stream()
                .filter(row -> row.ownerId() == ownerId)
                .count();
    }

    public synchronized int storedCount(int chestId) {
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
                    itemId,
                    new StoredRow(
                            itemId,
                            row.baseItemId(),
                            row.extraData(),
                            "",
                            row.limitedData(),
                            row.guildId(),
                            row.expires(),
                            false,
                            sequence));
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
            this.items.put(
                    itemId,
                    new InventoryRow(
                            toUserId,
                            row.baseItemId(),
                            row.extraData(),
                            row.limitedData(),
                            row.guildId(),
                            row.expires()));
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

    private boolean userExists(int userId) {
        return userId > 0 && !this.unknownUsers.contains(userId);
    }

    @Override
    public synchronized Release releaseChest(int chestId, int ownerId) {
        this.releaseCalls.add(chestId);
        ChestRow row = this.chests.get(chestId);
        if (row == null) return Release.of(ReleaseStatus.NO_CHEST, chestId, ownerId);

        List<Integer> ids =
                this.loadItems(chestId).stream().map(StoredRow::itemId).toList();
        if (ids.isEmpty() && row.coins() <= 0) {
            this.chests.remove(chestId);
            this.chestOwners.remove(chestId);
            return Release.of(ReleaseStatus.EMPTY, chestId, ownerId);
        }

        int owner = 0;
        InventoryRow chestItem = this.items.get(chestId);
        if (this.userExists(ownerId)) owner = ownerId;
        else if (chestItem != null && this.userExists(chestItem.ownerId())) owner = chestItem.ownerId();
        else if (this.userExists(this.chestOwners.getOrDefault(chestId, 0))) owner = this.chestOwners.get(chestId);
        if (owner <= 0) return Release.of(ReleaseStatus.OWNER_UNKNOWN, chestId, 0);

        Map<Integer, Integer> moved = this.withdrawItems(chestId, owner, ids);
        int paid = 0;
        int creditsAfter = -1;
        if (row.coins() > 0) {
            if (this.ledger.putIfAbsent(ChestRelease.operationId(chestId), row.coins()) == null) {
                this.credits.merge(owner, row.coins(), Integer::sum);
                paid = row.coins();
            }
            creditsAfter = this.credits.getOrDefault(owner, 0);
        }
        this.chests.remove(chestId);
        this.chestOwners.remove(chestId);
        return new Release(ReleaseStatus.RELEASED, chestId, owner, moved, paid, creditsAfter);
    }

    @Override
    public synchronized Set<Integer> chestIdsAmong(Collection<Integer> itemIds) {
        Set<Integer> found = new HashSet<>();
        for (Integer id : itemIds) {
            if (id != null && this.chests.containsKey(id)) found.add(id);
        }
        return found;
    }

    @Override
    public synchronized List<OrphanRow> orphanedChests() {
        List<OrphanRow> orphans = new ArrayList<>();
        for (int chestId : this.chests.keySet().stream().sorted().toList()) {
            boolean exists = this.items.containsKey(chestId)
                    || this.placedItems.contains(chestId)
                    || this.stored.containsKey(chestId);
            if (!exists) orphans.add(new OrphanRow(chestId, this.chestOwners.getOrDefault(chestId, 0)));
        }
        return orphans;
    }
}
