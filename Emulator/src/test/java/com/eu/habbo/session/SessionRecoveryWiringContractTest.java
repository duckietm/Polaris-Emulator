package com.eu.habbo.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SessionRecoveryWiringContractTest {

    @Test
    void migrationStoresOnlyDigestAndOneTimeRecoveryState() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V20260816130000__session_recovery_tickets.sql"));

        assertTrue(sql.contains("token_hash BINARY(32)"));
        assertTrue(sql.contains("recoverable_until"));
        assertTrue(sql.contains("consumed_at"));
        assertTrue(!sql.toLowerCase(java.util.Locale.ROOT).contains("token_plain"));
    }
}
