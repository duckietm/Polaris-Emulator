package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolInputGuardTest {
    @Test
    void normalizesNullableMessages() {
        assertEquals("", ModToolInputGuard.normalize(null));
        assertEquals("warn", ModToolInputGuard.normalize("  warn  "));
    }

    @Test
    void staffMessagesMustBeNonEmptyAndBounded() {
        assertFalse(ModToolInputGuard.isSafeMessage(null));
        assertFalse(ModToolInputGuard.isSafeMessage(""));
        assertTrue(ModToolInputGuard.isSafeMessage("a".repeat(ModToolInputGuard.MAX_MESSAGE_LENGTH)));
        assertFalse(ModToolInputGuard.isSafeMessage("a".repeat(ModToolInputGuard.MAX_MESSAGE_LENGTH + 1)));
    }
}
