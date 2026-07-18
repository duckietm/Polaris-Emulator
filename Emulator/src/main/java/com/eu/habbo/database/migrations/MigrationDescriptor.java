package com.eu.habbo.database.migrations;

import java.util.Objects;

public record MigrationDescriptor(
        int version,
        String description,
        String scriptName,
        String sql,
        String checksumSha256) {
    public MigrationDescriptor {
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(scriptName, "scriptName");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(checksumSha256, "checksumSha256");
        if (!checksumSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksumSha256 must be a lowercase SHA-256 value");
        }
    }
}
