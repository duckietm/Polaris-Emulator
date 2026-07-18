package com.eu.habbo.database.migrations;

import com.eu.habbo.core.ConfigurationManager;

import java.util.Map;
import java.util.Objects;

public record MigrationOptions(MigrationMode mode, boolean migrationsOnly, int lockTimeoutSeconds) {
    private static final String MODE_ARGUMENT_PREFIX = "--migrations=";

    public MigrationOptions {
        Objects.requireNonNull(mode, "mode");
        if (lockTimeoutSeconds < 1 || lockTimeoutSeconds > 60) {
            throw new IllegalArgumentException(
                    "db.migrations.lock_timeout_seconds must be between 1 and 60");
        }
    }

    public static MigrationOptions resolve(
            ConfigurationManager config,
            String[] arguments,
            Map<String, String> environment) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");

        String configuredMode = config.getValueIfPresent("db.migrations.mode");
        String selectedMode = configuredMode == null ? "validate" : configuredMode;
        String environmentMode = environment.get("DB_MIGRATIONS_MODE");
        if (environmentMode != null && !environmentMode.isBlank()) {
            selectedMode = environmentMode;
        }

        boolean migrationsOnly = false;
        for (String argument : arguments) {
            if ("--migrations-only".equals(argument)) {
                migrationsOnly = true;
            } else if (argument.startsWith(MODE_ARGUMENT_PREFIX)) {
                selectedMode = argument.substring(MODE_ARGUMENT_PREFIX.length());
            } else {
                throw new IllegalArgumentException("Unknown command-line argument: " + argument);
            }
        }

        String configuredTimeout = config.getValueIfPresent("db.migrations.lock_timeout_seconds");
        int lockTimeoutSeconds = configuredTimeout == null ? 10 : parseLockTimeout(configuredTimeout);
        return new MigrationOptions(MigrationMode.parse(selectedMode), migrationsOnly, lockTimeoutSeconds);
    }

    private static int parseLockTimeout(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "db.migrations.lock_timeout_seconds must be an integer between 1 and 60",
                    error);
        }
    }
}
