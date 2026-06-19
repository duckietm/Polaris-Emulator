package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashesVerifyWithSamePassword() {
        String hash = PasswordHasher.hash("correct horse battery staple", 4);

        assertTrue(PasswordHasher.verify("correct horse battery staple", hash));
        assertFalse(PasswordHasher.verify("wrong password", hash));
    }

    @Test
    void verifiesLaravelStyle2yHashes() {
        String hash = PasswordHasher.hash("secret-password", 4);
        String laravelStyle = "$2y$" + hash.substring(4);

        assertTrue(PasswordHasher.verify("secret-password", laravelStyle));
        assertFalse(PasswordHasher.verify("not-secret", laravelStyle));
    }

    @Test
    void invalidHashesFailClosed() {
        assertFalse(PasswordHasher.verify("password", ""));
        assertFalse(PasswordHasher.verify("password", "not-a-bcrypt-hash"));
        assertFalse(PasswordHasher.verify(null, "$2y$04$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuuabcde"));
    }
}
