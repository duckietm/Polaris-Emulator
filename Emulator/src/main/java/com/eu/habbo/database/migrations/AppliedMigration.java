package com.eu.habbo.database.migrations;

import java.time.Instant;
import java.util.Objects;

public record AppliedMigration(
        int version,
        String description,
        String scriptName,
        String checksumSha256,
        Instant installedOn,
        long executionMs) {
    public AppliedMigration {
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        description = Objects.requireNonNull(description, "description");
        scriptName = Objects.requireNonNull(scriptName, "scriptName");
        checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256");
        installedOn = Objects.requireNonNull(installedOn, "installedOn");
        if (executionMs < 0) throw new IllegalArgumentException("executionMs must not be negative");
    }
}
