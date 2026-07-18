package com.eu.habbo.database.observability;

import com.eu.habbo.core.ConfigurationManager;

public record SlowQuerySettings(boolean enabled, long thresholdMs, int maxSqlLength) {

    public static final String ENABLED_KEY = "db.slow_query.enabled";
    public static final String THRESHOLD_MS_KEY = "db.slow_query.threshold_ms";
    public static final String MAX_SQL_LENGTH_KEY = "db.slow_query.max_sql_length";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_THRESHOLD_MS = 250;
    public static final int DEFAULT_MAX_SQL_LENGTH = 1_024;

    public SlowQuerySettings {
        if (thresholdMs < 1) {
            throw new IllegalArgumentException(THRESHOLD_MS_KEY + " must be at least 1");
        }
        if (maxSqlLength < 128 || maxSqlLength > 8_192) {
            throw new IllegalArgumentException(MAX_SQL_LENGTH_KEY + " must be between 128 and 8192");
        }
    }

    public static SlowQuerySettings from(ConfigurationManager config) {
        return new SlowQuerySettings(
                config.getBoolean(ENABLED_KEY, DEFAULT_ENABLED),
                config.getInt(THRESHOLD_MS_KEY, DEFAULT_THRESHOLD_MS),
                config.getInt(MAX_SQL_LENGTH_KEY, DEFAULT_MAX_SQL_LENGTH));
    }

    public static SlowQuerySettings defaults() {
        return new SlowQuerySettings(
                DEFAULT_ENABLED,
                DEFAULT_THRESHOLD_MS,
                DEFAULT_MAX_SQL_LENGTH);
    }
}
