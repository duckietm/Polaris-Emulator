package com.eu.habbo.habbohotel.economy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyLedgerContractTest {
    @Test
    void locksWalletChecksIdempotencyMutatesAndAuditsOnOneConnection() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/economy/EconomyLedger.java"));

        int lock = source.indexOf("lockBalance(connection");
        int existing = source.indexOf("existingResult(connection", lock);
        int persist = source.indexOf("persistBalance(connection", existing);
        int audit = source.indexOf("EconomyAuditLogger.record(connection", persist);

        assertTrue(lock > -1);
        assertTrue(lock < existing, "wallet lock must serialize duplicate operation checks");
        assertTrue(existing < persist, "idempotent retry must return before another mutation");
        assertTrue(persist < audit, "the ledger row must describe the balance actually persisted");
        assertTrue(source.contains("FOR UPDATE"));
        assertTrue(source.contains("operation id reused with different payload"));
    }
}
