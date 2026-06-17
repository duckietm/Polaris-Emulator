package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolTicketGuardTest {
    @Test
    void idsMustBePositive() {
        assertFalse(ModToolTicketGuard.isPositiveId(0));
        assertFalse(ModToolTicketGuard.isPositiveId(-1));
        assertTrue(ModToolTicketGuard.isPositiveId(1));
    }

    @Test
    void releaseBatchIsBounded() {
        assertFalse(ModToolTicketGuard.isValidReleaseBatch(0));
        assertTrue(ModToolTicketGuard.isValidReleaseBatch(ModToolTicketGuard.MAX_RELEASE_BATCH));
        assertFalse(ModToolTicketGuard.isValidReleaseBatch(ModToolTicketGuard.MAX_RELEASE_BATCH + 1));
    }
}
