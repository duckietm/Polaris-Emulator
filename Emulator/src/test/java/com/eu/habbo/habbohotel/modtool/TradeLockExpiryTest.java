package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code sanctions.trade_locked_until} holds an absolute timestamp. The trade-lock
 * packet carries a length, and writing that length straight into the column put every
 * lock in 1970: the next login read it as expired and unlocked the user, "permanent"
 * included.
 */
class TradeLockExpiryTest {

    private static final int NOW = 1_800_000_000;

    @Test
    void aLengthBecomesAnExpiryInTheFuture() {
        // "Lock trade 1 week" is 168 hours.
        assertEquals(NOW + 604800, ModToolManager.banExpiry(NOW, 604800));
        assertEquals(NOW + 3600, ModToolManager.banExpiry(NOW, 3600));
    }

    @Test
    void anExpiryThatWouldOverflowIsClamped() {
        // "Lock trade permanent" asks for the largest length the field can carry.
        assertEquals(Integer.MAX_VALUE, ModToolManager.banExpiry(NOW, Integer.MAX_VALUE));
    }

    @Test
    void theStoredValueIsAlwaysAheadOfTheLoginCheck() {
        // SecureLoginEvent unlocks when trade_locked_until <= now. The raw length used
        // to land here, which for a week was 604800 - the 8th of January 1970.
        assertTrue(ModToolManager.banExpiry(NOW, 604800) > NOW);
        assertTrue(604800 < NOW, "the raw length on its own is long past");
    }
}
