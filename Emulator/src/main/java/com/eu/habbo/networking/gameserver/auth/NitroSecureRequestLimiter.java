package com.eu.habbo.networking.gameserver.auth;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Requests per client bucket and minute for the secure asset and API endpoints. Each request costs a key exchange
 * and, for a file, the encryption of up to 16 MB, with no limit before; a client over its share gets a 429.
 */
final class NitroSecureRequestLimiter {
    private static final int MAX_ENTRIES = 100_000;

    // Counted for a minute from the first request of the bucket: the entry is written once, then only incremented.
    private static final Map<String, AtomicInteger> REQUESTS = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(Duration.ofMinutes(1))
            .<String, AtomicInteger>build()
            .asMap();

    private NitroSecureRequestLimiter() {}

    /** Whether one more request of {@code scope} from {@code ip} fits in {@code perMinute}. */
    static boolean tryAcquire(String scope, String ip, int perMinute) {
        if (perMinute <= 0) return true;
        String key = scope + "|" + ClientAddressBucket.of(ip == null ? "" : ip);
        return REQUESTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet() <= perMinute;
    }

    /** Visible for tests. */
    static void resetForTests() {
        REQUESTS.clear();
    }
}
