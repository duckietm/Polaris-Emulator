package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogPageAccessPolicyTest {

    @Test
    void allowsEnabledPagesWithinTheUsersRank() {
        assertTrue(CatalogPageAccessPolicy.canOpen(1, 1, true, false));
    }

    @Test
    void allowsDisabledPagesOnlyToCatalogIdStaff() {
        assertTrue(CatalogPageAccessPolicy.canOpen(1, 7, false, true));
        assertFalse(CatalogPageAccessPolicy.canOpen(1, 7, false, false));
    }

    @Test
    void neverBypassesThePageRank() {
        assertFalse(CatalogPageAccessPolicy.canOpen(8, 7, true, true));
        assertFalse(CatalogPageAccessPolicy.canOpen(8, 7, false, true));
    }
}
