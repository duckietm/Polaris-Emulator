package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The open-pages effect handed the client whatever string was typed, and it sells at rank 1. These
 * pin the shape of the destination check: a fixed set anyone may open, everything else elevated.
 */
class WiredLinkPolicyTest {

    private static Habbo superWired() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);
        return habbo;
    }

    private static Habbo ordinary() {
        return mock(Habbo.class);
    }

    @Test
    @DisplayName("a leading slash and stray spaces do not change where the link points")
    void normalizationIsStable() {
        assertEquals("catalog/open", WiredLinkPolicy.normalize("  /catalog/open  "));
        assertEquals("catalog/open", WiredLinkPolicy.normalize("///catalog/open"));
        assertEquals("", WiredLinkPolicy.normalize(null));
        assertEquals("catalog", WiredLinkPolicy.namespaceOf("/CATALOG/open/1"));
    }

    @Test
    @DisplayName("the player-facing destinations are open to anyone")
    void safeNamespacesNeedNoPermission() {
        for (String link :
                new String[] {"catalog/open", "navigator/goto/12", "habbopages/help", "inventory/show", "achievements"
                }) {
            assertTrue(WiredLinkPolicy.isSafe(link), link);
            assertTrue(WiredLinkPolicy.canUse(link, ordinary()), link);
        }
    }

    @Test
    @DisplayName("staff surfaces and unknown destinations are not")
    void staffAndUnknownNamespacesAreElevated() {
        for (String link :
                new String[] {"mod-tools/open", "housekeeping", "furni-editor", "floor-editor", "not-a-real-namespace"
                }) {
            assertFalse(WiredLinkPolicy.isSafe(link), link);
            assertFalse(WiredLinkPolicy.canUse(link, ordinary()), link);
            assertTrue(WiredLinkPolicy.canUse(link, superWired()), link);
        }
    }

    @Test
    @DisplayName("an empty link is refused whoever asks")
    void anEmptyLinkIsAlwaysRefused() {
        assertFalse(WiredLinkPolicy.canUse("", superWired()));
        assertFalse(WiredLinkPolicy.canUse("   ", superWired()));
        assertFalse(WiredLinkPolicy.canUse(null, superWired()));
    }

    @Test
    @DisplayName("a null configurer never clears an elevated destination")
    void aMissingConfigurerIsRefused() {
        assertFalse(WiredLinkPolicy.canUse("mod-tools/open", null));
        assertTrue(WiredLinkPolicy.canUse("catalog/open", null));
    }
}
