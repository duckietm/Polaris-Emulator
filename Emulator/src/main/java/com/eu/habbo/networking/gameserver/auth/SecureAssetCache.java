package com.eu.habbo.networking.gameserver.auth;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Minified asset bytes keyed by file, bounded by their total size so a large gamedata tree cannot
 * grow the heap without limit. The least recently used entries go first.
 */
final class SecureAssetCache {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier maxBytes;
    private long totalBytes;

    SecureAssetCache(LongSupplier maxBytes) {
        this.maxBytes = maxBytes;
    }

    synchronized byte[] get(String key, long modified) {
        Entry entry = this.entries.get(key);
        if (entry == null) return null;
        if (entry.modified != modified) {
            this.entries.remove(key);
            this.totalBytes -= entry.bytes.length;
            return null;
        }
        return entry.bytes;
    }

    synchronized void put(String key, long modified, byte[] bytes) {
        Entry previous = this.entries.remove(key);
        if (previous != null) this.totalBytes -= previous.bytes.length;

        long limit = this.maxBytes.getAsLong();
        if (bytes.length > limit) return;

        this.entries.put(key, new Entry(modified, bytes));
        this.totalBytes += bytes.length;

        Iterator<Map.Entry<String, Entry>> eldest = this.entries.entrySet().iterator();
        while (this.totalBytes > limit && eldest.hasNext()) {
            this.totalBytes -= eldest.next().getValue().bytes.length;
            eldest.remove();
        }
    }

    synchronized long totalBytes() {
        return this.totalBytes;
    }

    private record Entry(long modified, byte[] bytes) {}
}
