package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The base migration gave every hotel the same token secret, so anyone could sign a valid access token; and the
 * account lock counted "Admin" and "Ädmin" apart, although the database finds one account for both.
 */
class LoginSecretAndLockKeyTest {

    @Test
    void theSecretShippedWithTheBaseDatabaseIsNeverUsed() {
        assertTrue(AccessTokenService.isPublishedSecret(
                "vA9OXBF7W8VBryT4xuOHIRtlfID60A2Sy54CjQZ7Nb/nL9gQKnbQJ5iYkyJ58TtD"));
        assertFalse(AccessTokenService.isPublishedSecret("a-secret-this-hotel-chose"));
        assertFalse(AccessTokenService.isPublishedSecret(null));
    }

    @Test
    void namesTheDatabaseTreatsAsOneShareOneLockCounter() {
        assertEquals("admin", AuthRateLimiter.accountKey("Admin"));
        assertEquals("admin", AuthRateLimiter.accountKey("ADMIN"));
        assertEquals("admin", AuthRateLimiter.accountKey("Ädmin"));
        assertEquals("admin", AuthRateLimiter.accountKey(" àdmìn "));
        assertNull(AuthRateLimiter.accountKey("  "));
        assertNull(AuthRateLimiter.accountKey(null));
    }
}
