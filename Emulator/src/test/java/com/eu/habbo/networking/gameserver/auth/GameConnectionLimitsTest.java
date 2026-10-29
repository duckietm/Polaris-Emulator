package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameConnectionLimitsTest {

    private static final GameConnectionLimits.Settings SETTINGS =
            new GameConnectionLimits.Settings(2, 3, 300_000L, 600_000L);

    @Test
    void visitorIsTheForwardedAddressElseAnUntrustedPeer() {
        assertEquals("203.0.113.9", GameConnectionLimits.visitorOf("203.0.113.9", "127.0.0.1", true));
        assertEquals("198.51.100.4", GameConnectionLimits.visitorOf(null, "198.51.100.4", false));
        assertNull(GameConnectionLimits.visitorOf(null, "127.0.0.1", true));
        assertNull(GameConnectionLimits.visitorOf("", null, false));
        assertEquals("2001:db8:1:2::/64", GameConnectionLimits.visitorOf("2001:db8:1:2::7", null, true));
    }

    @Test
    void connectionsAreCappedPerVisitorAndReleasedOnClose() {
        GameConnectionLimits limits = new GameConnectionLimits();
        assertTrue(limits.tryOpen("a", SETTINGS));
        assertTrue(limits.tryOpen("a", SETTINGS));
        assertFalse(limits.tryOpen("a", SETTINGS));
        assertEquals(2, limits.openConnections("a"));
        assertTrue(limits.tryOpen("b", SETTINGS));

        limits.close("a");
        assertTrue(limits.tryOpen("a", SETTINGS));
        limits.close("a");
        limits.close("a");
        limits.close("a");
        assertEquals(0, limits.openConnections("a"));
    }

    @Test
    void unlimitedVisitorsAndAZeroCapAreNotCounted() {
        GameConnectionLimits limits = new GameConnectionLimits();
        GameConnectionLimits.Settings off = new GameConnectionLimits.Settings(0, 0, 1L, 1L);
        for (int i = 0; i < 5; i++) {
            assertTrue(limits.tryOpen(null, SETTINGS));
            assertTrue(limits.tryOpen("a", off));
        }
        assertEquals(0, limits.openConnections("a"));
    }

    @Test
    void aConnectionOpenedWithTheLimitOffReleasesNothingOnClose() {
        GameConnectionLimits limits = new GameConnectionLimits();
        GameConnectionLimits.Settings off = new GameConnectionLimits.Settings(0, 0, 1L, 1L);
        assertFalse(GameConnectionLimits.isCounted("a", off));
        assertFalse(GameConnectionLimits.isCounted(null, SETTINGS));
        assertTrue(GameConnectionLimits.isCounted("a", SETTINGS));

        // Opened while the limit was off, then the limit is turned back on and two connections are counted.
        assertTrue(limits.tryOpen("a", off));
        assertTrue(limits.tryOpen("a", SETTINGS));
        assertTrue(limits.tryOpen("a", SETTINGS));
        if (GameConnectionLimits.isCounted("a", off)) {
            limits.close("a");
        }
        assertEquals(2, limits.openConnections("a"));
        assertFalse(limits.tryOpen("a", SETTINGS));
    }

    @Test
    void repeatedFailedLoginsBlockTheVisitorForTheBlockTime() {
        GameConnectionLimits limits = new GameConnectionLimits();
        long now = 1_000_000L;
        limits.recordFailedLogin("a", SETTINGS, now);
        limits.recordFailedLogin("a", SETTINGS, now + 1);
        assertFalse(limits.isLoginBlocked("a", now + 2));
        limits.recordFailedLogin("a", SETTINGS, now + 2);
        assertTrue(limits.isLoginBlocked("a", now + 3));
        assertFalse(limits.isLoginBlocked("b", now + 3));
        assertTrue(limits.isLoginBlocked("a", now + 2 + 599_999L));
        assertFalse(limits.isLoginBlocked("a", now + 2 + 600_000L));
    }

    @Test
    void failuresOutsideTheWindowStartOver() {
        GameConnectionLimits limits = new GameConnectionLimits();
        long now = 1_000_000L;
        limits.recordFailedLogin("a", SETTINGS, now);
        limits.recordFailedLogin("a", SETTINGS, now + 1);
        limits.recordFailedLogin("a", SETTINGS, now + 300_000L);
        assertFalse(limits.isLoginBlocked("a", now + 300_001L));
    }
}
