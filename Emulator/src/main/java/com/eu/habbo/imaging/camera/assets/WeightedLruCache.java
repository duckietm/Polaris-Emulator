package com.eu.habbo.imaging.camera.assets;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * A least-recently-used cache bounded by the summed weight of its values (for images: their
 * decoded size in bytes) rather than by entry count. An entry heavier than the whole budget is
 * not kept. All methods are synchronized; values are expected to be immutable once cached.
 */
public final class WeightedLruCache<K, V> {
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(64, 0.75f, true);
    private final ToLongFunction<V> weigher;
    private final long maxWeight;
    private long weight;

    public WeightedLruCache(long maxWeight, ToLongFunction<V> weigher) {
        this.maxWeight = Math.max(0, maxWeight);
        this.weigher = weigher;
    }

    public synchronized V get(K key) {
        return this.entries.get(key);
    }

    public synchronized boolean containsKey(K key) {
        return this.entries.containsKey(key);
    }

    public synchronized void put(K key, V value) {
        long valueWeight = value == null ? 0 : Math.max(0, this.weigher.applyAsLong(value));
        V previous = this.entries.remove(key);
        if (previous != null) {
            this.weight -= this.weigher.applyAsLong(previous);
        }
        if (valueWeight > this.maxWeight) {
            return;
        }
        this.entries.put(key, value);
        this.weight += valueWeight;
        Iterator<Map.Entry<K, V>> iterator = this.entries.entrySet().iterator();
        while (this.weight > this.maxWeight && iterator.hasNext()) {
            Map.Entry<K, V> eldest = iterator.next();
            if (eldest.getValue() != null) {
                this.weight -= this.weigher.applyAsLong(eldest.getValue());
            }
            iterator.remove();
        }
    }

    public synchronized void clear() {
        this.entries.clear();
        this.weight = 0;
    }

    public synchronized long weight() {
        return this.weight;
    }

    public synchronized int size() {
        return this.entries.size();
    }
}
