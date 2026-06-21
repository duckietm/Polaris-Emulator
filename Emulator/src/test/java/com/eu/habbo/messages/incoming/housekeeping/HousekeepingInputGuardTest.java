package com.eu.habbo.messages.incoming.housekeeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingInputGuardTest {
    @Test
    void normalizesNullableText() {
        assertEquals("", HousekeepingInputGuard.normalize(null));
        assertEquals("hello", HousekeepingInputGuard.normalize("  hello  "));
    }

    @Test
    void enforcesInclusiveLengthLimits() {
        assertTrue(HousekeepingInputGuard.isWithinLimit("abc", 3));
        assertFalse(HousekeepingInputGuard.isWithinLimit("abcd", 3));
        assertFalse(HousekeepingInputGuard.isWithinLimit(null, 3));
    }

    @Test
    void auditValuesCollapseControlWhitespaceAndCapLength() {
        String value = HousekeepingInputGuard.auditValue(" one\r\ntwo\tthree ");

        assertEquals("one  two three", value);

        String oversized = "x".repeat(HousekeepingInputGuard.MAX_REASON_LENGTH + 1);
        assertEquals(HousekeepingInputGuard.MAX_REASON_LENGTH, HousekeepingInputGuard.auditValue(oversized).length());
    }
}
