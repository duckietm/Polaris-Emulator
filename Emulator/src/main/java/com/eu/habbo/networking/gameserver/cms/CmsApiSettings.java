package com.eu.habbo.networking.gameserver.cms;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime configuration for the CMS API, sourced from the {@code emulator_api}
 * database table rather than {@code config.ini}, so a hotel owner (or the CMS
 * itself) can rotate keys and tune limits live without editing files or
 * restarting.
 *
 * <p>The table is a simple {@code key}/{@code value} store, read into a short-lived
 * cache. Any key missing from the table — or the whole table being absent (for
 * example before the migration has run) or the database being briefly unavailable
 * — falls back to the value registered in {@link Emulator#getConfig()}, which
 * carries the built-in defaults. That keeps the API working safely at all times
 * and means a fresh row only ever tightens or overrides a default, never breaks
 * startup.
 *
 * <p>{@code cms.api.enabled} and {@code cms.api.allowed} are intentionally NOT read
 * here: enabling the surface and the network allow-list stay in {@code config.ini}
 * as bootstrap/security settings.
 */
public final class CmsApiSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsApiSettings.class);
    static final String TABLE = "emulator_api";
    private static final long DEFAULT_TTL_MILLIS = 15_000L;

    // A single shared instance (final, so it is not a mutable static field). Its
    // lambdas are evaluated lazily, so class initialization touches neither the
    // database nor Emulator config.
    private static final CmsApiSettings INSTANCE = new CmsApiSettings(
            CmsApiSettings::loadFromDatabase,
            (key, def) -> {
                var config = Emulator.getConfig();
                return config != null ? config.getValue(key, def) : def;
            },
            DEFAULT_TTL_MILLIS,
            System::currentTimeMillis);

    private final Supplier<Map<String, String>> loader;
    private final BiFunction<String, String, String> fallback;
    private final long ttlMillis;
    private final LongSupplier clockMillis;

    private volatile Map<String, String> cache = Map.of();
    private volatile boolean loaded = false;
    private volatile long loadedAt;

    CmsApiSettings(
            Supplier<Map<String, String>> loader,
            BiFunction<String, String, String> fallback,
            long ttlMillis,
            LongSupplier clockMillis) {
        this.loader = loader;
        this.fallback = fallback;
        this.ttlMillis = ttlMillis;
        this.clockMillis = clockMillis;
    }

    /** Process-wide instance backed by the {@code emulator_api} table and config defaults. */
    public static CmsApiSettings get() {
        return INSTANCE;
    }

    public String getValue(String key, String defaultValue) {
        String value = current().get(key);
        return value != null ? value : this.fallback.apply(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = getValue(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            LOGGER.warn("[cms-api] setting {} has non-integer value '{}'; using {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key, defaultValue ? "1" : "0");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true" -> true;
            case "0", "false" -> false;
            default -> defaultValue;
        };
    }

    private Map<String, String> current() {
        long now = this.clockMillis.getAsLong();
        if (this.loaded && now - this.loadedAt <= this.ttlMillis) {
            return this.cache;
        }
        synchronized (this) {
            if (this.loaded && this.clockMillis.getAsLong() - this.loadedAt <= this.ttlMillis) {
                return this.cache;
            }
            Map<String, String> reloaded;
            try {
                reloaded = this.loader.get();
            } catch (Exception e) {
                LOGGER.warn("[cms-api] could not load {} settings; using config defaults", TABLE, e);
                reloaded = Map.of();
            }
            this.cache = reloaded == null ? Map.of() : reloaded;
            this.loadedAt = this.clockMillis.getAsLong();
            this.loaded = true;
            return this.cache;
        }
    }

    private static Map<String, String> loadFromDatabase() {
        Map<String, String> map = new HashMap<>();
        com.eu.habbo.database.Database database = Emulator.getDatabase();
        if (database == null) {
            return map;
        }
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT `key`, `value` FROM " + TABLE);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                map.put(result.getString("key"), result.getString("value"));
            }
        } catch (Exception e) {
            // Missing table (pre-migration) or a transient DB blip — fall back to
            // config defaults rather than failing the request.
            LOGGER.debug("[cms-api] {} not readable; using config defaults", TABLE, e);
        }
        return map;
    }
}
