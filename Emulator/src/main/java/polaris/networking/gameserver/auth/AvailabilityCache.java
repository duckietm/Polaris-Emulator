package polaris.networking.gameserver.auth;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import polaris.Emulator;

public final class AvailabilityCache {

    private static final Map<String, Entry> EMAIL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Entry> USERNAME_CACHE = new ConcurrentHashMap<>();

    private AvailabilityCache() {}

    public static Boolean lookupEmail(String email) {
        return read(EMAIL_CACHE, key(email));
    }

    public static Boolean lookupUsername(String username) {
        return read(USERNAME_CACHE, key(username));
    }

    public static void storeEmail(String email, boolean available) {
        write(EMAIL_CACHE, key(email), available);
    }

    public static void storeUsername(String username, boolean available) {
        write(USERNAME_CACHE, key(username), available);
    }

    public static void invalidateEmail(String email) {
        EMAIL_CACHE.remove(key(email));
    }

    public static void invalidateUsername(String username) {
        USERNAME_CACHE.remove(key(username));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Boolean read(Map<String, Entry> cache, String key) {
        if (!isEnabled() || key.isEmpty()) return null;
        Entry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.available;
    }

    private static void write(Map<String, Entry> cache, String key, boolean available) {
        if (!isEnabled() || key.isEmpty()) return;

        int maxEntries = configInt("login.probe.cache_max_entries", 10_000);
        if (cache.size() >= maxEntries) evict(cache, maxEntries);

        long ttlMs = configInt("login.probe.cache_ttl_sec", 60) * 1000L;
        cache.put(key, new Entry(available, System.currentTimeMillis() + ttlMs));
    }

    private static void evict(Map<String, Entry> cache, int maxEntries) {
        long now = System.currentTimeMillis();
        cache.values().removeIf(e -> e.expiresAt < now);

        if (cache.size() < maxEntries) return;

        int overflow = cache.size() - maxEntries + 1;
        Iterator<String> it = cache.keySet().iterator();
        while (overflow > 0 && it.hasNext()) {
            it.next();
            it.remove();
            overflow--;
        }
    }

    private static boolean isEnabled() {
        return Emulator.getConfig() == null || Emulator.getConfig().getBoolean("login.probe.cache_enabled", true);
    }

    private static int configInt(String key, int fallback) {
        return Emulator.getConfig() != null ? Emulator.getConfig().getInt(key, fallback) : fallback;
    }

    private record Entry(boolean available, long expiresAt) {}
}
