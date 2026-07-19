package com.eu.habbo.database.backup;

import com.eu.habbo.core.ConfigurationManager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record DatabaseBackupOptions(
        boolean enabled,
        Path directory,
        int retentionCount,
        Duration timeout,
        String executable) {

    public DatabaseBackupOptions {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        timeout = Objects.requireNonNull(timeout, "timeout");
        executable = Objects.requireNonNull(executable, "executable").trim();
        if (retentionCount < 1 || retentionCount > 1000) {
            throw new IllegalArgumentException("backup retention must be between 1 and 1000");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("backup timeout must be between 1 second and 1 day");
        }
        if (executable.isEmpty()) {
            throw new IllegalArgumentException("backup executable must not be blank");
        }
    }

    public static DatabaseBackupOptions resolve(ConfigurationManager config) {
        Objects.requireNonNull(config, "config");
        return new DatabaseBackupOptions(
                config.getBoolean("db.migrations.backup.enabled", true),
                Path.of(config.getValue("db.migrations.backup.directory", "backups/database")),
                config.getInt("db.migrations.backup.keep", 7),
                Duration.ofSeconds(config.getInt("db.migrations.backup.timeout_seconds", 900)),
                config.getValue("db.migrations.backup.executable", "mariadb-dump"));
    }
}
