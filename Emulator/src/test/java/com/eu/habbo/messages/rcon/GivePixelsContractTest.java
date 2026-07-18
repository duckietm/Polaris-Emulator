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

        assertTrue(source.contains("EconomyLedger.execute"),
                "Offline RCON pixel grants must use the immutable transactional ledger");
        assertTrue(source.contains("RconGrantGuard.validatePositiveAmount"),
                "RCON pixel grants must reject zero, negative, and oversized grants");
        assertTrue(source.contains("RconUserLookup.userExists"),
                "Offline RCON pixel grants must not create orphan currency rows for missing users");
    }
}
