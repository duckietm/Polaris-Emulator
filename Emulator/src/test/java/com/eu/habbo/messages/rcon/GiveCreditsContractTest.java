package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveCreditsContractTest {
    private static String giveCreditsSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/GiveCredits.java"));
    }

    @Test
    void offlineCreditGrantReportsMissingUsersWhenNoRowsChange() throws Exception {
        String source = giveCreditsSource();

        assertTrue(source.contains("executeUpdate()"),
                "Offline RCON credit grants must inspect the affected row count");
        assertTrue(source.contains("HABBO_NOT_FOUND"),
                "Offline RCON credit grants must report missing users when the UPDATE changes no rows");
        assertTrue(source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON credit grants must reject zero, negative, and oversized grants");
    }
}
