package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SecureAssetCacheTest {

    private static byte[] bytes(int size) {
        return new byte[size];
    }

    @Test
    void returnsTheBytesOnlyWhileTheFileIsUnchanged() {
        SecureAssetCache cache = new SecureAssetCache(() -> 100);
        byte[] value = {1, 2, 3};

        cache.put("a", 10L, value);

        assertArrayEquals(value, cache.get("a", 10L));
        assertNull(cache.get("a", 11L));
        assertNull(cache.get("a", 10L), "a stale entry is dropped, not kept");
        assertEquals(0, cache.totalBytes());
    }

    @Test
    void evictsTheLeastRecentlyUsedEntryWhenTheLimitIsExceeded() {
        SecureAssetCache cache = new SecureAssetCache(() -> 100);

        cache.put("a", 1L, bytes(40));
        cache.put("b", 1L, bytes(40));
        cache.get("a", 1L);
        cache.put("c", 1L, bytes(40));

        assertEquals(80, cache.totalBytes());
        assertNull(cache.get("b", 1L), "b was the least recently used");
        assertEquals(40, cache.get("a", 1L).length);
        assertEquals(40, cache.get("c", 1L).length);
    }

    @Test
    void doesNotKeepAnEntryLargerThanTheWholeLimit() {
        SecureAssetCache cache = new SecureAssetCache(() -> 100);

        cache.put("small", 1L, bytes(10));
        cache.put("huge", 1L, bytes(101));

        assertNull(cache.get("huge", 1L));
        assertEquals(10, cache.get("small", 1L).length, "an oversized entry must not evict the others");
    }

    @Test
    void replacingAnEntryDoesNotCountItTwice() {
        SecureAssetCache cache = new SecureAssetCache(() -> 100);

        cache.put("a", 1L, bytes(60));
        cache.put("a", 2L, bytes(70));

        assertEquals(70, cache.totalBytes());
        assertEquals(70, cache.get("a", 2L).length);
    }

    @Test
    void followsALimitThatChangesAtRuntime() {
        long[] limit = {100};
        SecureAssetCache cache = new SecureAssetCache(() -> limit[0]);

        cache.put("a", 1L, bytes(60));
        limit[0] = 50;
        cache.put("b", 1L, bytes(20));

        assertNull(cache.get("a", 1L));
        assertEquals(20, cache.totalBytes());
    }
}
