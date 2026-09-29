package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-user permission values (table user_permission_overrides) that come before the rank and the
 * plugins: a timed sanction (trade = 0 for 7 days) or a grant (an event host's key for a day).
 * Each user's rows are cached for a few minutes, so a row written from outside (a CMS) is seen
 * without a reload; a change made in game drops the cache at once.
 */
public final class UserPermissionOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPermissionOverrides.class);
    static final long CACHE_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHED_USERS = 20_000;

    public record UserOverride(
            String key, PermissionSetting setting, int expiresAt, String reason, int createdBy, String createdByName) {
        /** Permanent (0) or not yet expired. */
        public boolean activeAt(int now) {
            return this.expiresAt <= 0 || this.expiresAt > now;
        }
    }

    private record Loaded(Map<String, UserOverride> byKey, long loadedAt) {}

    private final ConcurrentHashMap<Integer, Loaded> cache = new ConcurrentHashMap<>();
    private final IntFunction<List<UserOverride>> loader;
    private final LongSupplier millis;
    private final IntSupplier now;

    public UserPermissionOverrides() {
        this(UserPermissionOverrides::loadFromDatabase, System::currentTimeMillis, WiredPlatform::unixTimestamp);
    }

    UserPermissionOverrides(IntFunction<List<UserOverride>> loader, LongSupplier millis, IntSupplier now) {
        this.loader = loader;
        this.millis = millis;
        this.now = now;
    }

    /** The user's active value for the key, or null when there is none. */
    public PermissionSetting find(int userId, String key) {
        UserOverride override = this.findOverride(userId, key);
        return override == null ? null : override.setting();
    }

    public UserOverride findOverride(int userId, String key) {
        if (userId <= 0 || key == null) {
            return null;
        }

        UserOverride override = this.loaded(userId).byKey().get(key);
        return override != null && override.activeAt(this.now.getAsInt()) ? override : null;
    }

    public List<UserOverride> active(int userId) {
        int time = this.now.getAsInt();
        List<UserOverride> active = new ArrayList<>();

        for (UserOverride override : this.loaded(userId).byKey().values()) {
            if (override.activeAt(time)) {
                active.add(override);
            }
        }

        active.sort((a, b) -> a.key().compareTo(b.key()));
        return active;
    }

    public void invalidate(int userId) {
        this.cache.remove(userId);
    }

    private Loaded loaded(int userId) {
        long time = this.millis.getAsLong();
        Loaded loaded = this.cache.get(userId);

        if (loaded != null && time - loaded.loadedAt() < CACHE_MS) {
            return loaded;
        }

        Map<String, UserOverride> byKey = new HashMap<>();
        for (UserOverride override : this.loader.apply(userId)) {
            byKey.put(override.key(), override);
        }

        if (this.cache.size() >= MAX_CACHED_USERS) {
            this.cache.entrySet().removeIf(entry -> time - entry.getValue().loadedAt() >= CACHE_MS);
        }

        loaded = new Loaded(Map.copyOf(byKey), time);
        this.cache.put(userId, loaded);
        return loaded;
    }

    /** Writes (or replaces) the user's value for the key; expiresAt 0 is permanent. */
    public static void save(
            int userId,
            String key,
            PermissionSetting setting,
            int expiresAt,
            String reason,
            int actorId,
            String actorName) {
        SqlQueries.update(
                "INSERT INTO user_permission_overrides (user_id, permission_key, value, expires_at, reason, created_by, created_by_name, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE value = VALUES(value), expires_at = VALUES(expires_at), reason = VALUES(reason),"
                        + " created_by = VALUES(created_by), created_by_name = VALUES(created_by_name), created_at = VALUES(created_at)",
                userId,
                key,
                valueOf(setting),
                Math.max(0, expiresAt),
                reason == null ? "" : reason,
                actorId,
                actorName == null ? "" : actorName,
                WiredPlatform.unixTimestamp());
    }

    public static boolean delete(int userId, String key) {
        return SqlQueries.update(
                        "DELETE FROM user_permission_overrides WHERE user_id = ? AND permission_key = ? LIMIT 1",
                        userId,
                        key)
                > 0;
    }

    private static List<UserOverride> loadFromDatabase(int userId) {
        if (WiredPlatform.database() == null) {
            return List.of();
        }

        try {
            return SqlQueries.query(
                    "SELECT permission_key, value, expires_at, reason, created_by, created_by_name"
                            + " FROM user_permission_overrides WHERE user_id = ?",
                    rs -> new UserOverride(
                            rs.getString("permission_key"),
                            PermissionSetting.fromString(Integer.toString(rs.getInt("value"))),
                            rs.getInt("expires_at"),
                            rs.getString("reason"),
                            rs.getInt("created_by"),
                            rs.getString("created_by_name")),
                    userId);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Failed to load permission overrides of user {}", userId, e);
            return List.of();
        }
    }

    public static int valueOf(PermissionSetting setting) {
        return switch (setting) {
            case ALLOWED -> 1;
            case ROOM_OWNER -> 2;
            case DISALLOWED -> 0;
        };
    }

    /**
     * A duration like 30m, 12h, 7d or 2w in seconds; "perm", "permanent" or "0" is 0 (no end).
     * Anything else is -1.
     */
    public static int parseDuration(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }

        String value = text.trim().toLowerCase(Locale.ROOT);

        if (value.equals("0") || value.equals("perm") || value.equals("permanent")) {
            return 0;
        }

        if (value.length() < 2) {
            return -1;
        }

        long unit =
                switch (value.charAt(value.length() - 1)) {
                    case 's' -> 1L;
                    case 'm' -> 60L;
                    case 'h' -> 3600L;
                    case 'd' -> 86400L;
                    case 'w' -> 604800L;
                    default -> -1L;
                };

        if (unit < 0) {
            return -1;
        }

        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
            long seconds = amount * unit;
            return amount > 0 && seconds <= Integer.MAX_VALUE / 2 ? (int) seconds : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
