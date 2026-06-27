package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Contents of a wired storage chest (Phase-2, config-based v1). A chest holds a list of
 * {@link Entry} rows — either currency ({@link #KIND_CURRENCY}) or furni base-types
 * ({@link #KIND_FURNI}). The list is persisted as JSON in the chest furni's own
 * {@code items.wired_data} column (no dedicated table), serialized via
 * {@link WiredManager#getGson()}.
 *
 * <p>Pure model — no room/DB access — so it is unit-testable. The owning
 * {@code InteractionWiredChest*} furni loads/saves it; the give effects and chest conditions read
 * and mutate it through {@link #take(int, int, int)} / {@link #count(int, int)} etc.</p>
 */
public class ChestStorage {
    public static final int KIND_CURRENCY = 0;
    public static final int KIND_FURNI = 1;

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

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return this.entries;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** Total quantity across every entry of a kind. */
    public int total(int kind) {
        int sum = 0;
        for (Entry e : this.entries) {
            if (e.kind == kind) {
                sum += Math.max(0, e.quantity);
            }
        }
        return sum;
    }

    /** Quantity held for a specific kind+type. */
    public int count(int kind, int type) {
        int sum = 0;
        for (Entry e : this.entries) {
            if (e.kind == kind && e.type == type) {
                sum += Math.max(0, e.quantity);
            }
        }
        return sum;
    }

    public boolean has(int kind, int type, int quantity) {
        return this.count(kind, type) >= quantity;
    }

    /** Distinct types present for a kind, in insertion order. */
    public List<Integer> distinctTypes(int kind) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (Entry e : this.entries) {
            if (e.kind == kind && e.quantity > 0) {
                seen.add(e.type);
            }
        }
        return new ArrayList<>(seen);
    }

    /** Add quantity to a kind+type, merging into the existing entry if present. No-op for quantity &lt;= 0. */
    public void add(int kind, int type, int quantity) {
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
    public int take(int kind, int type, int quantity) {
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

    public String toJson() {
        return WiredManager.getGson().toJson(new JsonData(this.entries));
    }

    /** Parse from a wired_data JSON string. Null / blank / malformed → an empty chest (never throws). */
    public static ChestStorage fromJson(String json) {
        ChestStorage chest = new ChestStorage();
        if (json == null || json.isEmpty() || !json.startsWith("{")) {
            return chest;
        }
        try {
            JsonData data = WiredManager.getGson().fromJson(json, JsonData.class);
            if (data != null && data.entries != null) {
                for (Entry e : data.entries) {
                    if (e != null && e.quantity > 0) {
                        chest.add(e.kind, e.type, e.quantity);
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed payload → empty chest
        }
        return chest;
    }

    static class JsonData {
        List<Entry> entries;

        JsonData() {
        }

        JsonData(List<Entry> entries) {
            this.entries = entries;
        }
    }
}
