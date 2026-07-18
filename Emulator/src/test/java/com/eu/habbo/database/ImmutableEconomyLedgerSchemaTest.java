package com.eu.habbo.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmutableEconomyLedgerSchemaTest {
    @Test
    void migrationMakesEconomyAuditAppendOnlyAndSelfDescribing() throws Exception {
        String sql = Files.readString(Path.of("../Database/Database Updates/028_immutable_economy_ledger.sql"));

        assertTrue(sql.contains("`actor_id` INT UNSIGNED NULL"));
        assertTrue(sql.contains("`reason` VARCHAR(96) NOT NULL"));
        assertTrue(sql.contains("BEFORE UPDATE ON `logs_economy`"));
        assertTrue(sql.contains("BEFORE DELETE ON `logs_economy`"));
        assertTrue(sql.contains("SIGNAL SQLSTATE '45000'"));
    }
}
