package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoTicketExpiryContractTest {
    @Test
    void builtInIssuersRegisterEmulatorManagedTicketSessions() throws Exception {
        String endpoints = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

        assertTrue(count(endpoints, "SsoTicketPolicy.register(conn") >= 2,
                "password and remember login must register a hashed SSO session");
        assertTrue(endpoints.contains("auth_ticket = '', online = '0'"),
                "logout must clear the CMS-compatible auth_ticket field");
        assertTrue(!endpoints.contains("auth_ticket_expires_at"),
                "runtime authentication must not require CMS-managed expiry metadata");
    }

    @Test
    void everySsoEntryPointUsesTheEmulatorManagedResolver() throws Exception {
        String combined = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java")
                + read("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java")
                + read("src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java");

        assertTrue(count(combined, "SsoTicketPolicy.resolve(") >= 4,
                "HTTP exchange, logout, ghost resume, and game login must use the same fixed-deadline resolver");
    }

    @Test
    void resolverStoresOnlyTicketHashAndDoesNotExtendExistingDeadline() throws Exception {
        String policy = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SsoTicketPolicy.java");

        assertTrue(policy.contains("MessageDigest.getInstance(\"SHA-256\")"),
                "raw SSO bearer values must not be stored in the session table");
        assertTrue(policy.contains("INSERT IGNORE INTO users_auth_ticket_sessions"),
                "first use should enroll tickets without overwriting an existing expiry");
        assertTrue(policy.contains("expires_at >= NOW()"),
                "subsequent uses must enforce the original fixed deadline");
        assertTrue(policy.contains("SELECT id FROM users WHERE auth_ticket = ?"),
                "unchanged external CMS issuers must remain compatible");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int count(String text, String needle) {
        int matches = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            matches++;
            offset += needle.length();
        }
        return matches;
    }
}
