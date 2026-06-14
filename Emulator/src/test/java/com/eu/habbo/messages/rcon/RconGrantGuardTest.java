package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RconGrantGuardTest {
    @Test
    void validatesPositiveGrantAmounts() {
        assertNull(RconGrantGuard.validatePositiveAmount(1, 100, "credits"));
        assertEquals("invalid credits", RconGrantGuard.validatePositiveAmount(0, 100, "credits"));
        assertEquals("invalid credits", RconGrantGuard.validatePositiveAmount(-1, 100, "credits"));
        assertEquals("credits exceeds rcon grant ceiling", RconGrantGuard.validatePositiveAmount(101, 100, "credits"));
    }

    @Test
    void validatesNonNegativeGrantAmounts() {
        assertNull(RconGrantGuard.validateNonNegativeAmount(0, 100, "respect_given"));
        assertEquals("invalid respect_given", RconGrantGuard.validateNonNegativeAmount(-1, 100, "respect_given"));
        assertEquals("respect_given exceeds rcon grant ceiling", RconGrantGuard.validateNonNegativeAmount(101, 100, "respect_given"));
    }

    @Test
    void validatesUserAndCurrencyIdentifiers() {
        assertNull(RconGrantGuard.validateUserId(1));
        assertEquals("invalid user", RconGrantGuard.validateUserId(0));
        assertNull(RconGrantGuard.validateCurrencyType(0));
        assertEquals("invalid currency type", RconGrantGuard.validateCurrencyType(-1));
    }

    @Test
    void parsesInvalidGrantCeilingsAsDefault() {
        assertEquals(RconGrantGuard.DEFAULT_MAX_AMOUNT, RconGrantGuard.parseMaxAmount(null));
        assertEquals(RconGrantGuard.DEFAULT_MAX_AMOUNT, RconGrantGuard.parseMaxAmount("0"));
        assertEquals(500, RconGrantGuard.parseMaxAmount("500"));
    }
}
