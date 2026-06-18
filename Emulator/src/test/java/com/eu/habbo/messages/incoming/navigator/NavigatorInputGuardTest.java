package com.eu.habbo.messages.incoming.navigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigatorInputGuardTest {
    @Test
    void searchValuesAreTrimmedAndBounded() {
        assertEquals("", NavigatorInputGuard.normalizeSearch(null));
        assertEquals("rare", NavigatorInputGuard.normalizeSearch("  rare  "));
        assertEquals(NavigatorInputGuard.MAX_SEARCH_LENGTH, NavigatorInputGuard.normalizeSearch("a".repeat(100)).length());
    }

    @Test
    void savedSearchValuesUseLargerBound() {
        assertEquals("", NavigatorInputGuard.normalizeSavedSearchValue(null));
        assertEquals("owner:duckie", NavigatorInputGuard.normalizeSavedSearchValue(" owner:duckie "));
        assertEquals(NavigatorInputGuard.MAX_SAVED_SEARCH_LENGTH, NavigatorInputGuard.normalizeSavedSearchValue("a".repeat(400)).length());
    }
}
