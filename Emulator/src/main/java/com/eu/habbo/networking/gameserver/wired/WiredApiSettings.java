package com.eu.habbo.networking.gameserver.wired;

import com.eu.habbo.core.ConfigurationManager;
import java.util.ArrayList;
import java.util.List;

/** The variables web API configuration, read per request so a config reload applies at once. */
record WiredApiSettings(
        boolean enabled,
        int maxPayloadBytes,
        boolean rateLimitEnabled,
        int perIp,
        int perIpWindowMs,
        int perKey,
        int perKeyWindowMs,
        int roomWrites,
        int roomWritesWindowMs,
        int authFailMax,
        int authFailWindowMs,
        int authFailBlockMs,
        int maxBatch,
        int maxBulkNames,
        int maxPageSize,
        List<String> corsOrigins) {

    static final int DEFAULT_MAX_PAYLOAD_BYTES = 16 * 1024;

    static WiredApiSettings defaults(boolean enabled) {
        return new WiredApiSettings(
                enabled,
                DEFAULT_MAX_PAYLOAD_BYTES,
                true,
                120,
                10_000,
                60,
                10_000,
                200,
                10_000,
                10,
                60_000,
                300_000,
                100,
                20,
                100,
                List.of("*"));
    }

    static WiredApiSettings from(ConfigurationManager config) {
        if (config == null) {
            return defaults(false);
        }
        WiredApiSettings d = defaults(false);
        return new WiredApiSettings(
                config.getBoolean("wired.api.enabled", false),
                bounded(config, "wired.api.max_payload_bytes", d.maxPayloadBytes(), 256, 1024 * 1024),
                config.getBoolean("wired.api.rate_limit.enabled", true),
                bounded(config, "wired.api.rate_limit.per_ip", d.perIp(), 1, 100_000),
                bounded(config, "wired.api.rate_limit.per_ip.window_ms", d.perIpWindowMs(), 100, 3_600_000),
                bounded(config, "wired.api.rate_limit.per_key", d.perKey(), 1, 100_000),
                bounded(config, "wired.api.rate_limit.per_key.window_ms", d.perKeyWindowMs(), 100, 3_600_000),
                bounded(config, "wired.api.rate_limit.room_writes", d.roomWrites(), 1, 100_000),
                bounded(config, "wired.api.rate_limit.room_writes.window_ms", d.roomWritesWindowMs(), 100, 3_600_000),
                bounded(config, "wired.api.auth_fail.max", d.authFailMax(), 1, 10_000),
                bounded(config, "wired.api.auth_fail.window_ms", d.authFailWindowMs(), 1000, 86_400_000),
                bounded(config, "wired.api.auth_fail.block_ms", d.authFailBlockMs(), 1000, 86_400_000),
                bounded(config, "wired.api.batch.max", d.maxBatch(), 1, 1000),
                bounded(config, "wired.api.bulk_delete.max", d.maxBulkNames(), 1, 200),
                bounded(config, "wired.api.page_size.max", d.maxPageSize(), 1, 1000),
                origins(config.getValue("wired.api.cors.origins", "*")));
    }

    private static int bounded(ConfigurationManager config, String key, int fallback, int min, int max) {
        int value = config.getInt(key, fallback);
        return value < min || value > max ? fallback : value;
    }

    static List<String> origins(String raw) {
        List<String> origins = new ArrayList<>();
        if (raw == null) {
            return List.of("*");
        }
        for (String origin : raw.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                origins.add(trimmed);
            }
        }
        return origins.isEmpty() ? List.of() : List.copyOf(origins);
    }

    /**
     * The value for Access-Control-Allow-Origin, or null when the origin is not allowed. An entry
     * without a scheme ({@code camwijs.eu}) allows that host over http and https; a trailing slash is
     * ignored.
     */
    String allowedOrigin(String origin) {
        if (this.corsOrigins.contains("*")) {
            return "*";
        }
        if (origin == null) {
            return null;
        }
        String presented = stripSlash(origin);
        String host = presented.contains("://") ? presented.substring(presented.indexOf("://") + 3) : null;
        for (String allowed : this.corsOrigins) {
            String entry = stripSlash(allowed);
            boolean match = entry.contains("://")
                    ? entry.equalsIgnoreCase(presented)
                    : host != null && entry.equalsIgnoreCase(host) && isWebScheme(presented);
            if (match) {
                return origin;
            }
        }
        return null;
    }

    private static String stripSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static boolean isWebScheme(String origin) {
        String lower = origin.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
