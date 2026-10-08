package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModToolBanExpiryTest {
    private static final int NOW = 1_790_622_185;

    @Test
    void aTimedBanEndsAfterItsDuration() {
        assertEquals(NOW + 3600, ModToolManager.banExpiry(NOW, 3600));
    }

    @Test
    void aPermanentBanStaysInTheFutureInsteadOfWrappingNegative() {
        assertEquals(Integer.MAX_VALUE, ModToolManager.banExpiry(NOW, Integer.MAX_VALUE));
    }
}
