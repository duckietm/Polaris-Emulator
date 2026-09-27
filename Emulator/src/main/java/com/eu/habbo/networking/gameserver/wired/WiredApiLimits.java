package com.eu.habbo.networking.gameserver.wired;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Flood protection for the variables web API: requests per client address before authentication,
 * a lockout after repeated failed authentications, requests per key and variable writes per room.
 * Every table is bounded and expires, so a flood of addresses or keys cannot grow memory.
 */
final class WiredApiLimits {
    private static final int MAX_ENTRIES = 16_384;
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(65);

    /** Where a limiter stands after a request: shown in the X-RateLimit headers. */
    record Window(int limit, int remaining, long resetSeconds) {}

    private final LongSupplier clockMillis;
    private final Cache<String, FixedWindow> perIp = newCache();
    private final Cache<String, FixedWindow> perKey = newCache();
    private final Cache<Integer, FixedWindow> roomWrites = newCache();
    private final Cache<String, FixedWindow> authFailures = newCache();
    private final Cache<Integer, Long> lastBulkDelete = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(Duration.ofMillis(BULK_DELETE_COOLDOWN_MS))
            .build();

    /** A bulk delete clears stored values of holders who are not even in the room: once a minute. */
    static final long BULK_DELETE_COOLDOWN_MS = 60_000L;

    private final Cache<String, Long> blockedUntil = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(Duration.ofHours(25))
            .build();

    WiredApiLimits(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    private static <K> Cache<K, FixedWindow> newCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterAccess(IDLE_EXPIRY)
                .build();
    }

    /** Refuses a client address that failed authentication too often. */
    void checkNotBlocked(String ip) {
        Long until = this.blockedUntil.getIfPresent(ip);
        long now = this.clockMillis.getAsLong();
        if (until != null && until > now) {
            throw WiredApiException.rateLimited(ceilSeconds(until - now), -1);
        }
    }

    void recordAuthFailure(String ip, WiredApiSettings settings) {
        long now = this.clockMillis.getAsLong();
        FixedWindow window = this.authFailures.get(ip, ignored -> new FixedWindow());
        if (!window.tryAcquire(now, settings.authFailMax(), settings.authFailWindowMs(), 1)
                .allowed()) {
            this.blockedUntil.put(ip, now + settings.authFailBlockMs());
            this.authFailures.invalidate(ip);
        }
    }

    Window acquireIp(String ip, WiredApiSettings settings) {
        return this.acquire(this.perIp, ip, settings, settings.perIp(), settings.perIpWindowMs(), 1);
    }

    Window acquireKey(String keyId, WiredApiSettings settings) {
        return this.acquire(this.perKey, keyId, settings, settings.perKey(), settings.perKeyWindowMs(), 1);
    }

    void acquireBulkDelete(int roomId) {
        long now = this.clockMillis.getAsLong();
        synchronized (this.lastBulkDelete) {
            Long last = this.lastBulkDelete.getIfPresent(roomId);
            if (last != null && now - last < BULK_DELETE_COOLDOWN_MS && now >= last) {
                throw WiredApiException.rateLimited(ceilSeconds(last + BULK_DELETE_COOLDOWN_MS - now), 1);
            }
            this.lastBulkDelete.put(roomId, now);
        }
    }

    void acquireRoomWrites(int roomId, int writes, WiredApiSettings settings) {
        if (writes <= 0) {
            return;
        }
        this.acquire(this.roomWrites, roomId, settings, settings.roomWrites(), settings.roomWritesWindowMs(), writes);
    }

    private <K> Window acquire(
            Cache<K, FixedWindow> cache, K key, WiredApiSettings settings, int limit, int windowMs, int count) {
        if (!settings.rateLimitEnabled()) {
            return null;
        }
        long now = this.clockMillis.getAsLong();
        Outcome outcome = cache.get(key, ignored -> new FixedWindow()).tryAcquire(now, limit, windowMs, count);
        if (!outcome.allowed()) {
            throw WiredApiException.rateLimited(outcome.window().resetSeconds(), limit);
        }
        return outcome.window();
    }

    static long ceilSeconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private record Outcome(boolean allowed, Window window) {}

    private static final class FixedWindow {
        private long start = Long.MIN_VALUE;
        private int used;

        synchronized Outcome tryAcquire(long now, int limit, int windowMs, int count) {
            if (this.start == Long.MIN_VALUE || now - this.start >= windowMs || now < this.start) {
                this.start = now;
                this.used = 0;
            }
            long reset = ceilSeconds(this.start + windowMs - now);
            if (this.used + (long) count > limit) {
                return new Outcome(false, new Window(limit, Math.max(0, limit - this.used), reset));
            }
            this.used += count;
            return new Outcome(true, new Window(limit, limit - this.used, reset));
        }
    }
}
