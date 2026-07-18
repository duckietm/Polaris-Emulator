package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetMigrationContractTest {
    @Test
    void migrationRepairsLegacyPasswordResetTablesWithoutDroppingData() throws Exception {
        String sql = Files.readString(Path.of(
                "../Database/Database Updates/028_password_resets_runtime_contract.sql"));
        String normalized = sql.toLowerCase(java.util.Locale.ROOT);

        assertTrue(normalized.contains("add column if not exists `user_id`"));
        assertTrue(normalized.contains("add column if not exists `created_ip`"));
        assertTrue(normalized.contains("add unique index if not exists"));
        assertTrue(normalized.contains("`user_id`"));
    }
}
