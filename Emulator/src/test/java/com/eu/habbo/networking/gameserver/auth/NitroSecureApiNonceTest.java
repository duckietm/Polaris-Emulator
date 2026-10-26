package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The replay cache is fed by unauthenticated requests, so the nonce is bounded
 * before it can ever be stored: the client mints 16 random bytes as hex, and a
 * 30 KB nonce only exists to fill the cache with heap the caller controls.
 */
class NitroSecureApiNonceTest {

    @Test
    void acceptsTheNonceTheClientMints() {
        // secure-assets.ts: randomHex(16)
        assertTrue(NitroSecureApiHandler.isNonceAcceptable("0f1e2d3c4b5a69788796a5b4c3d2e1f0"));
        assertTrue(NitroSecureApiHandler.isNonceAcceptable("A".repeat(16)));
        assertTrue(NitroSecureApiHandler.isNonceAcceptable("A".repeat(128)));
        assertTrue(NitroSecureApiHandler.isNonceAcceptable("nonce-1.2:3_4/5+6="));
    }

    @Test
    void rejectsANonceThatIsOnlyThereToFillTheCache() {
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("A".repeat(129)));
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("A".repeat(30_000)));
    }

    @Test
    void rejectsANonceTooShortToBeUnpredictable() {
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("deadbeef"));
        assertFalse(NitroSecureApiHandler.isNonceAcceptable(""));
        assertFalse(NitroSecureApiHandler.isNonceAcceptable(null));
    }

    @Test
    void rejectsCharactersTheClientNeverSends() {
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("0f1e2d3c4b5a6978 8796a5b4c3d2e1f0"));
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("0f1e2d3c4b5a6978\n8796a5b4c3d2e1f0"));
        assertFalse(NitroSecureApiHandler.isNonceAcceptable("{\"a\":\"bbbbbbbbbbbbbbbbbb\"}"));
    }
}
