package com.eu.habbo.database.integrity;

import com.eu.habbo.core.ConfigurationManager;

import java.util.Objects;

public record IntegrityAuditSettings(
        IntegrityAuditMode mode,
        int sampleLimit,
        int queryTimeoutSeconds,
        int maxDurationSeconds) {

    private static final String KEY_MODE = "db.integrity.audit.mode";
    private static final String KEY_SAMPLE_LIMIT = "db.integrity.audit.sample_limit";
    private static final String KEY_QUERY_TIMEOUT = "db.integrity.audit.query_timeout_seconds";
    private static final String KEY_MAX_DURATION = "db.integrity.audit.max_duration_seconds";

    public IntegrityAuditSettings {
        Objects.requireNonNull(mode, "mode");
        if (sampleLimit < 0 || sampleLimit > 100) {
            throw new IllegalArgumentException(
                    KEY_SAMPLE_LIMIT + " must be between 0 and 100");
        }
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 300) {
            throw new IllegalArgumentException(
                    KEY_QUERY_TIMEOUT + " must be between 1 and 300 seconds");
        }
        if (maxDurationSeconds < 1 || maxDurationSeconds > 1800) {
            throw new IllegalArgumentException(
                    KEY_MAX_DURATION + " must be between 1 and 1800 seconds");
        }
    }

    public static IntegrityAuditSettings resolve(
            ConfigurationManager config,
            IntegrityAuditOptions options) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(options, "options");
        IntegrityAuditMode configured = IntegrityAuditMode.parse(
                config.getValue(KEY_MODE, "warn"));
        return new IntegrityAuditSettings(
                options.modeOverride().orElse(configured),
                config.getInt(KEY_SAMPLE_LIMIT, 5),
                config.getInt(KEY_QUERY_TIMEOUT, 30),
                config.getInt(KEY_MAX_DURATION, 120));
    }
}
