package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetHardeningContractTest {
    @Test
    void builtInConsumerLocksUpdatesRevokesAndConsumesAtomically() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/auth/PasswordResetEndpoints.java"));

        int transaction = source.indexOf("conn.setAutoCommit(false)");
        int lock = source.indexOf("FOR UPDATE", transaction);
        int password = source.indexOf("UPDATE users SET password = ?", lock);
        int revoke = source.indexOf("UPDATE users_remember_families SET revoked = 1", password);
        int consume = source.indexOf("DELETE FROM password_resets WHERE user_id = ?", revoke);
        int commit = source.indexOf("conn.commit()", consume);

        assertTrue(transaction > -1 && lock > transaction);
        assertTrue(password > lock && revoke > password && consume > revoke && commit > consume,
                "password update, credential revocation, and one-use consumption must share one transaction");
    }

    @Test
    void legacyStorageRemainsTheDefaultAndSecureModeHashesBeforeInsert() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        assertTrue(source.contains("password.reset.secure_storage.enabled\", false"),
                "existing external reset consumers must remain compatible by default");
        assertTrue(source.contains("PasswordResetPolicy.storedToken(token, secureStorage)"));
        assertTrue(source.contains("DELETE FROM password_resets WHERE expires_at < NOW()"));
    }
}
