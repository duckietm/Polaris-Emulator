package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GivePixelsContractTest {
    private static String givePixelsSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/GivePixels.java"));
    }

    @Test
    void offlinePixelGrantCreatesMissingCurrencyRow() throws Exception {
        String source = givePixelsSource();

        assertTrue(source.contains("INSERT INTO users_currency"),
                "Offline RCON pixel grants must create the users_currency type 0 row when it is missing");
        assertTrue(source.contains("ON DUPLICATE KEY UPDATE"),
                "Offline RCON pixel grants should increment existing rows with an upsert");
        assertTrue(source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON pixel grants must reject zero, negative, and oversized grants");
        assertTrue(source.contains("RconUserLookup.userExists"),
                "Offline RCON pixel grants must not create orphan currency rows for missing users");
    }
}
