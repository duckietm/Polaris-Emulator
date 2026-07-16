package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetPolicyTest {
    @Test
    void secureStorageKeepsOnlyDeterministicDigest() {
        String raw = "a-very-long-random-reset-token";
        String stored = PasswordResetPolicy.storedToken(raw, true);

        assertTrue(stored.startsWith("sha256:"));
        assertNotEquals(raw, stored);
        assertEquals(stored, PasswordResetPolicy.tokenDigest(raw));
        assertEquals(raw, PasswordResetPolicy.storedToken(raw, false));
    }

    @Test
    void secureModeAllowsHttpsAndLoopbackOnly() {
        assertTrue(PasswordResetPolicy.isSecureOrLoopbackUrl("https://hotel.example/reset"));
        assertTrue(PasswordResetPolicy.isSecureOrLoopbackUrl("http://127.0.0.1/reset"));
        assertTrue(PasswordResetPolicy.isSecureOrLoopbackUrl("http://localhost/reset"));
        assertFalse(PasswordResetPolicy.isSecureOrLoopbackUrl("http://hotel.example/reset"));
        assertFalse(PasswordResetPolicy.isSecureOrLoopbackUrl("not-a-url"));
    }
}
