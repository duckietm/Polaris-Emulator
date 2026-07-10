package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * State of a player-facing wired chest (Scrigno). Holds the stored CONTENTS (a list of {@link Entry}
 * rows — currency {@link #KIND_CURRENCY} or furni base-types {@link #KIND_FURNI}) PLUS the chest's
 * player-facing CONFIG (name / description / access flags / capacity / appearance / notification prefs).
 * Persisted as JSON in the chest furni's own {@code items.wired_data}, serialized via
 * {@link WiredManager#getGson()}. Old payloads ({@code {entries:[...]}}) still load — the config fields
 * just default.
 *
 * <p>Pure model — no room/DB access — so it stays unit-testable.</p>
 *
 * <p><b>Thread-safety:</b> a chest's contents are mutated from several threads — the per-channel
 * Netty handler for player packets (deposit/withdraw), other players' channels, and the room/wired
 * thread (wired chest effects and contract transactions). Every method that reads or mutates the
 * entries / stored furni / log therefore synchronizes on this instance, and the collection getters
 * return defensive snapshots so callers can iterate without racing a concurrent mutation. The
 * combined {@code depositCurrency} / {@code withdrawCurrency} / {@code tryDepositFurni} /
 * {@code removeFurniByWireType} operations are the atomic building blocks the packet handlers use so
 * a check-then-act can't be split by another thread (which is how items/currency would otherwise be
 * duplicated).</p>
 */
public class ChestStorage {
    public static final int KIND_CURRENCY = 0;
    public static final int KIND_FURNI = 1;

    public static final int DEFAULT_CAPACITY = 5000;
    public static final int CAPACITY_STEP = 5000;
    public static final int MAX_CAPACITY = 1_000_000;

    /** A single chest row. {@code type} is the currency type (KIND_CURRENCY) or base item id (KIND_FURNI). */
    public static class Entry {
        public int kind;
        public int type;
        public int quantity;

        public Entry() {
        }

        public Entry(int kind, int type, int quantity) {
            this.kind = kind;
            this.type = type;
            this.quantity = quantity;
        }
    }

    public static final int MAX_LOG = 50;

    /** A transaction-log row (deposit / withdraw), newest first. */
    public static class LogEntry {
        public String type;      // "deposit" | "withdraw"
        public long timestamp;   // epoch millis
        public String userName;
        public int withdrawn;
        public int deposited;

        public LogEntry() {
        }

        public LogEntry(String type, long timestamp, String userName, int withdrawn, int deposited) {
            this.type = type;
            this.timestamp = timestamp;
            this.userName = userName;
            this.withdrawn = withdrawn;
            this.deposited = deposited;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<LogEntry> log = new ArrayList<>();
    private final List<ChestFurniStoredItem> furniItems = new ArrayList<>();
    private int nextFurniInventoryId = 1;

    // --- player-facing config ---
    private String name = "";
    private String description = "";
    private boolean accessOpen = true;       // everyone can open
    private boolean accessDonate = false;    // everyone can donate
    private int capacityMax = DEFAULT_CAPACITY;
    private int appearanceState = 0;         // 0 = open when someone looks inside
    private boolean notifyFull = false;
    private boolean notifyDonation = false;
    private boolean notifyWithdraw = false;
    private boolean notifyEmpty = false;
    private boolean notifyWired = false;
    private int notifyMode = 0;              // 0 = always

    /** Defensive snapshot — safe to iterate off-thread; mutate the chest through the dedicated methods. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(this.entries);
    }

    /** Defensive snapshot (element references preserved, so field reads on the rows are still live). */
    public synchronized List<ChestFurniStoredItem> furniItems() {
        return new ArrayList<>(this.furniItems);
    }

    public synchronized int furniItemCount() {
        return this.furniItems.size();
    }

    /** Add one stored furni row (v2). Keeps aggregate {@link #KIND_FURNI} entries in sync for wired conditions. */
    public synchronized ChestFurniStoredItem addFurniItem(ChestFurniStoredItem item) {
        this.assignInventoryId(item);
        this.furniItems.add(item);
        this.add(KIND_FURNI, item.baseItemId, 1);
        return item;
    }

    /**
     * Capacity-guarded furni add. Returns false (and adds nothing) when the chest is already at
     * capacity, so a concurrent deposit can't push it over the limit. Atomic.
     */
    public synchronized boolean tryDepositFurni(ChestFurniStoredItem item) {
        if (item == null || this.furniItems.size() >= this.getCapacityMax()) {
            return false;
        }
        this.addFurniItem(item);
        return true;
    }

    /** Remove every stored furni row and clear aggregate {@link #KIND_FURNI} entries. */
    public synchronized List<ChestFurniStoredItem> removeAllFurniItems() {
        if (this.furniItems.isEmpty()) {
            return List.of();
        }
        List<ChestFurniStoredItem> removed = new ArrayList<>(this.furniItems);
        this.furniItems.clear();
        this.entries.removeIf(e -> e.kind == KIND_FURNI);
        return removed;
    }

    /** Remove up to {@code amount} rows matching a {@link ChestItemType} wire identity. */
    public synchronized List<ChestFurniStoredItem> removeFurniByType(boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        List<ChestFurniStoredItem> removed = new ArrayList<>();
        if (amount <= 0) return removed;

        String poster = legacyPosterId == null ? "" : legacyPosterId;
        var it = this.furniItems.iterator();
        while (it.hasNext() && removed.size() < amount) {
            ChestFurniStoredItem item = it.next();
            if (item.wallItem != wallItem || item.baseItemId != baseItemId) continue;
            String itemPoster = item.legacyPosterId == null ? "" : item.legacyPosterId;
            if (wallItem && !poster.equals(itemPoster)) continue;
            removed.add(item);
            it.remove();
            this.take(KIND_FURNI, baseItemId, 1);
        }
        return removed;
    }

    /** Floor-item shortcut for legacy withdraw wire [baseItemId, amount]. */
    public synchronized List<ChestFurniStoredItem> removeFurniByBaseItemId(int baseItemId, int amount) {
        return removeFurniByType(false, baseItemId, "", amount);
    }

    /**
     * Quantity of stored rows matching a CLIENT-facing type id ({@link ChestFurniStoredItem#wireTypeId()}
     * — the sprite id the client saw and echoes back on withdraw, not the internal base item id).
     */
    public synchronized int countFurniByWireType(boolean wallItem, int wireTypeId, String legacyPosterId) {
        String poster = legacyPosterId == null ? "" : legacyPosterId;
        int count = 0;
        for (ChestFurniStoredItem item : this.furniItems) {
            if (item.wallItem != wallItem || item.wireTypeId() != wireTypeId) continue;
            String itemPoster = item.legacyPosterId == null ? "" : item.legacyPosterId;
            if (wallItem && !poster.equals(itemPoster)) continue;
            count++;
        }
        return count;
    }

    /** Like {@link #removeFurniByType} but matching the CLIENT-facing type id (sprite id) instead. Atomic. */
    public synchronized List<ChestFurniStoredItem> removeFurniByWireType(boolean wallItem, int wireTypeId, String legacyPosterId, int amount) {
        List<ChestFurniStoredItem> removed = new ArrayList<>();
        if (amount <= 0) return removed;

        String poster = legacyPosterId == null ? "" : legacyPosterId;
        var it = this.furniItems.iterator();
        while (it.hasNext() && removed.size() < amount) {
            ChestFurniStoredItem item = it.next();
            if (item.wallItem != wallItem || item.wireTypeId() != wireTypeId) continue;
            String itemPoster = item.legacyPosterId == null ? "" : item.legacyPosterId;
            if (wallItem && !poster.equals(itemPoster)) continue;
            removed.add(item);
            it.remove();
            this.take(KIND_FURNI, item.baseItemId, 1);
        }
        return removed;
    }

    /** Expand legacy aggregate furni rows into per-item storage (called once on load). */
    public synchronized void migrateAggregatedFurniToItems() {
        if (!this.furniItems.isEmpty()) return;

        for (Entry e : this.entries) {
            if (e.kind != KIND_FURNI || e.quantity <= 0) continue;
            for (int i = 0; i < e.quantity; i++) {
                ChestFurniStoredItem item = new ChestFurniStoredItem();
                item.baseItemId = e.type;
                item.stuffDataFormat = ChestFurniWireUtil.LEGACY_FORMAT;
                item.extradata = "0";
                item.extra = 0;
                this.assignInventoryId(item);
                this.furniItems.add(item);
            }
        }
    }

    private void assignInventoryId(ChestFurniStoredItem item) {
        if (item.inventoryId <= 0) {
            item.inventoryId = this.nextFurniInventoryId++;
        } else if (item.inventoryId >= this.nextFurniInventoryId) {
            this.nextFurniInventoryId = item.inventoryId + 1;
        }
    }

    public synchronized boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** Total quantity across every entry of a kind. */
    public synchronized int total(int kind) {
        int sum = 0;
        for (Entry e : this.entries) {
            if (e.kind == kind) {
                sum += Math.max(0, e.quantity);
            }
        }
        return sum;
    }

    /** Quantity held for a specific kind+type. */
    public synchronized int count(int kind, int type) {
        int sum = 0;
        for (Entry e : this.entries) {
            if (e.kind == kind && e.type == type) {
                sum += Math.max(0, e.quantity);
            }
        }
        return sum;
    }

    public synchronized boolean has(int kind, int type, int quantity) {
        return this.count(kind, type) >= quantity;
    }

    /** Distinct types present for a kind, in insertion order. */
    public synchronized List<Integer> distinctTypes(int kind) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (Entry e : this.entries) {
            if (e.kind == kind && e.quantity > 0) {
                seen.add(e.type);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Capacity-guarded currency deposit. Accepts at most what fits under {@link #getCapacityMax()},
     * adds it, and returns the accepted amount (0 when full) so the caller debits the user by exactly
     * that. Atomic, so two concurrent deposits can't jointly overflow the capacity.
     */
    public synchronized int depositCurrency(int currencyType, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int capacityLeft = this.getCapacityMax() - this.total(KIND_CURRENCY);
        int accepted = Math.min(amount, capacityLeft);
        if (accepted <= 0) {
            return 0;
        }
        this.add(KIND_CURRENCY, currencyType, accepted);
        return accepted;
    }

    /**
     * Currency withdraw. {@code amount < 0} withdraws all of that type. Returns how much was actually
     * removed (never more than is present, never negative) so the caller credits the user by exactly
     * that. Atomic.
     */
    public synchronized int withdrawCurrency(int currencyType, int amount) {
        int available = this.count(KIND_CURRENCY, currencyType);
        if (available <= 0) {
            return 0;
        }
        int requested = (amount < 0) ? available : Math.min(amount, available);
        if (requested <= 0) {
            return 0;
        }
        return this.take(KIND_CURRENCY, currencyType, requested);
    }

    /** Add quantity to a kind+type, merging into the existing entry if present. No-op for quantity &lt;= 0. */
    public synchronized void add(int kind, int type, int quantity) {
        if (quantity <= 0) {
            return;
        }
        for (Entry e : this.entries) {
            if (e.kind == kind && e.type == type) {
                e.quantity += quantity;
                return;
            }
        }
        this.entries.add(new Entry(kind, type, quantity));
    }

    /**
     * Remove up to {@code quantity} of a specific kind+type. Returns how many were actually removed
     * (capped by what's available). Entries that reach zero are dropped. Never goes negative.
     */
    public synchronized int take(int kind, int type, int quantity) {
        if (quantity <= 0) {
            return 0;
        }
        int taken = 0;
        var it = this.entries.iterator();
        while (it.hasNext() && taken < quantity) {
            Entry e = it.next();
            if (e.kind != kind || e.type != type || e.quantity <= 0) {
                continue;
            }
            int remove = Math.min(e.quantity, quantity - taken);
            e.quantity -= remove;
            taken += remove;
            if (e.quantity <= 0) {
                it.remove();
            }
        }
        return taken;
    }

    // --- config getters/setters ---
    public String getName() { return this.name == null ? "" : this.name; }
    public void setName(String value) { this.name = (value == null) ? "" : value; }

    public String getDescription() { return this.description == null ? "" : this.description; }
    public void setDescription(String value) { this.description = (value == null) ? "" : value; }

    public boolean isAccessOpen() { return this.accessOpen; }
    public void setAccessOpen(boolean value) { this.accessOpen = value; }

    public boolean isAccessDonate() { return this.accessDonate; }
    public void setAccessDonate(boolean value) { this.accessDonate = value; }

    public int getCapacityMax() { return this.capacityMax <= 0 ? DEFAULT_CAPACITY : this.capacityMax; }
    public void setCapacityMax(int value) { this.capacityMax = Math.max(DEFAULT_CAPACITY, Math.min(MAX_CAPACITY, value)); }

    public int getAppearanceState() { return this.appearanceState; }
    public void setAppearanceState(int value) { this.appearanceState = value; }

    public boolean isNotifyFull() { return this.notifyFull; }
    public boolean isNotifyDonation() { return this.notifyDonation; }
    public boolean isNotifyWithdraw() { return this.notifyWithdraw; }
    public boolean isNotifyEmpty() { return this.notifyEmpty; }
    public boolean isNotifyWired() { return this.notifyWired; }
    public int getNotifyMode() { return this.notifyMode; }

    public void setNotifications(boolean full, boolean donation, boolean withdraw, boolean empty, boolean wired, int mode) {
        this.notifyFull = full;
        this.notifyDonation = donation;
        this.notifyWithdraw = withdraw;
        this.notifyEmpty = empty;
        this.notifyWired = wired;
        this.notifyMode = mode;
    }

    /** Defensive snapshot — safe to iterate off-thread. */
    public synchronized List<LogEntry> getLog() {
        return new ArrayList<>(this.log);
    }

    /** Record a transaction at the head of the log, keeping at most {@link #MAX_LOG} rows. */
    public synchronized void addLog(LogEntry entry) {
        if (entry == null) return;
        this.log.add(0, entry);
        while (this.log.size() > MAX_LOG) {
            this.log.remove(this.log.size() - 1);
        }
    }

    public synchronized String toJson() {
        JsonData data = new JsonData(this.entries);
        data.name = this.name;
        data.description = this.description;
        data.accessOpen = this.accessOpen;
        data.accessDonate = this.accessDonate;
        data.capacityMax = this.capacityMax;
        data.appearanceState = this.appearanceState;
        data.notifyFull = this.notifyFull;
        data.notifyDonation = this.notifyDonation;
        data.notifyWithdraw = this.notifyWithdraw;
        data.notifyEmpty = this.notifyEmpty;
        data.notifyWired = this.notifyWired;
        data.notifyMode = this.notifyMode;
        data.log = this.log;
        data.furniItems = this.furniItems;
        data.nextFurniInventoryId = this.nextFurniInventoryId;
        return WiredManager.getGson().toJson(data);
    }

    /** Parse from a wired_data JSON string. Null / blank / malformed → an empty chest (never throws). */
    public static ChestStorage fromJson(String json) {
        ChestStorage chest = new ChestStorage();
        if (json == null || json.isEmpty() || !json.startsWith("{")) {
            return chest;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(json, JsonData.class);
            if (data != null) {
                if (data.entries != null) {
                    for (Entry e : data.entries) {
                        if (e != null && e.quantity > 0) {
                            chest.add(e.kind, e.type, e.quantity);
                        }
                    }
                }
                chest.name = (data.name == null) ? "" : data.name;
                chest.description = (data.description == null) ? "" : data.description;
                chest.accessOpen = data.accessOpen;
                chest.accessDonate = data.accessDonate;
                chest.capacityMax = (data.capacityMax <= 0) ? DEFAULT_CAPACITY : data.capacityMax;
                chest.appearanceState = data.appearanceState;
                chest.notifyFull = data.notifyFull;
                chest.notifyDonation = data.notifyDonation;
                chest.notifyWithdraw = data.notifyWithdraw;
                chest.notifyEmpty = data.notifyEmpty;
                chest.notifyWired = data.notifyWired;
                chest.notifyMode = data.notifyMode;
                if (data.log != null) {
                    for (LogEntry le : data.log) {
                        if (le != null) chest.log.add(le);
                    }
                }
                if (data.furniItems != null) {
                    for (ChestFurniStoredItem fi : data.furniItems) {
                        if (fi != null) {
                            chest.assignInventoryId(fi);
                            chest.furniItems.add(fi);
                        }
                    }
                }
                if (data.nextFurniInventoryId > 0) {
                    chest.nextFurniInventoryId = data.nextFurniInventoryId;
                }
            }
            chest.migrateAggregatedFurniToItems();
        } catch (Exception ignored) {
            // malformed payload → empty chest
        }
        return chest;
    }

    static class JsonData {
        List<Entry> entries;
        String name = "";
        String description = "";
        boolean accessOpen = true;
        boolean accessDonate = false;
        int capacityMax = DEFAULT_CAPACITY;
        int appearanceState = 0;
        boolean notifyFull = false;
        boolean notifyDonation = false;
        boolean notifyWithdraw = false;
        boolean notifyEmpty = false;
        boolean notifyWired = false;
        int notifyMode = 0;
        List<LogEntry> log;
        List<ChestFurniStoredItem> furniItems;
        int nextFurniInventoryId = 1;

        JsonData() {
        }

        JsonData(List<Entry> entries) {
            this.entries = entries;
        }
    }
}
