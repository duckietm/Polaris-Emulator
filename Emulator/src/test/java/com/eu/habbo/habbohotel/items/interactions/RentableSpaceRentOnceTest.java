package com.eu.habbo.habbohotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Two users renting the same space at once both passed the "not rented" check and both paid; the last one became
 * the renter. A debit a plugin cancelled or the ledger refused still rented the space.
 */
class RentableSpaceRentOnceTest {

    @Test
    void theCheckAndTheRentShareOneLockAndTheChargeMustGoThrough() throws Exception {
        String source = Files.readString(Path.of(
                        "src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionRentableSpace.java"))
                .replaceAll("\\s+", " ");

        assertTrue(source.contains("synchronized (this) { int errorCode = this.getRentErrorCode(habbo);"));
        assertTrue(source.contains("&& !habbo.applyCredits( -cost,"));
        assertFalse(source.contains("habbo.giveCredits(-cost)"));
    }
}
