package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A variable name is cleaned on save (observed on AIR WIN63-202610201032). */
class WiredVariableNameCleaningTest {

    @Test
    void spacesBecomeUnderscoresCapitalsAreLoweredAndOuterUnderscoresGo() {
        assertEquals("a", WiredVariableNameValidator.normalizeForSave("_a"));
        assertEquals("9", WiredVariableNameValidator.normalizeForSave("9_"));
        assertEquals("a_b", WiredVariableNameValidator.normalizeForSave("a b"));
        assertEquals("ptrup", WiredVariableNameValidator.normalizeForSave("PtrUP"));
        assertEquals("a__b", WiredVariableNameValidator.normalizeForSave("a__b"));
        assertEquals("", WiredVariableNameValidator.normalizeForSave("__"));
    }

    @Test
    void aNameTooLongToSaveIsLeftAsSent() {
        String tooLong = "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno";
        assertEquals(tooLong, WiredVariableNameValidator.normalizeForSave(tooLong));
    }

    @Test
    void aStoredNameLoadsAsItWasSaved() {
        assertEquals("Old_Name", WiredVariableNameValidator.normalizeLegacy("Old Name"));
    }
}
