package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WiredCompatibilityDiagnosticsTest {

    @AfterEach
    void resetRateLimits() {
        WiredCompatibilityDiagnostics.resetForTesting();
    }

    @Test
    void eachFiniteFailurePointIsRateLimitedIndependently() {
        var first = WiredCompatibilityDiagnostics.FailurePoint.LEGACY_DATA_LIST_ENTRY;
        var second = WiredCompatibilityDiagnostics.FailurePoint.CHEST_STORAGE_NUMERIC_FIELD;

        assertTrue(WiredCompatibilityDiagnostics.shouldEmit(first, 1_000L));
        assertFalse(WiredCompatibilityDiagnostics.shouldEmit(first, 60_999L));
        assertTrue(WiredCompatibilityDiagnostics.shouldEmit(first, 61_000L));
        assertTrue(WiredCompatibilityDiagnostics.shouldEmit(second, 1_001L));
    }

    @Test
    void exceptionDescriptionCannotExposeMessageOrPayload() {
        IllegalArgumentException failure = new IllegalArgumentException("secret chat and command payload");

        assertEquals("IllegalArgumentException", WiredCompatibilityDiagnostics.exceptionType(failure));
    }
}
