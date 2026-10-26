package com.eu.habbo.messages.incoming.collectibles;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Moving every collectible to another account is a trade by another name, so the
 * mod tools' trade lock has to hold here too. This path had no check at all, which
 * made it the way around the sanction.
 */
class CollectibleTransferTradeLockTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/collectibles/TransferCollectiblesEvent.java");

    @Test
    void theTransferIsRefusedWhileTheUserIsTradeLocked() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("allowTrade()"), "the transfer must consult the trade lock");

        int guard = source.indexOf("allowTrade()");
        int payment = source.indexOf("CollectiblesSupport.pay(");
        int transfer = source.indexOf("service.transferAll(");

        assertTrue(payment > 0 && transfer > 0, source);
        assertTrue(guard < payment, "the lock is checked before the fee is taken");
        assertTrue(guard < transfer, "the lock is checked before anything moves");
    }
}
