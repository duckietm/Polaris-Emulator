package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The secure asset and API endpoints ran a key exchange, and for a file up to 16 MB of encryption, without a limit. */
class NitroSecureRequestLimiterTest {

    @AfterEach
    void reset() {
        NitroSecureRequestLimiter.resetForTests();
    }

    @Test
    void aClientOverItsShareIsRefused() {
        for (int i = 0; i < 3; i++) {
            assertTrue(NitroSecureRequestLimiter.tryAcquire("assets", "203.0.113.7", 3));
        }
        assertFalse(NitroSecureRequestLimiter.tryAcquire("assets", "203.0.113.7", 3));
    }

    @Test
    void eachClientAndEachEndpointHasItsOwnShare() {
        for (int i = 0; i < 3; i++) {
            NitroSecureRequestLimiter.tryAcquire("assets", "203.0.113.7", 3);
        }
        assertTrue(NitroSecureRequestLimiter.tryAcquire("assets", "198.51.100.20", 3));
        assertTrue(NitroSecureRequestLimiter.tryAcquire("api", "203.0.113.7", 3));
    }

    @Test
    void zeroTurnsTheLimitOff() {
        for (int i = 0; i < 10; i++) {
            assertTrue(NitroSecureRequestLimiter.tryAcquire("api", "203.0.113.7", 0));
        }
    }
}
