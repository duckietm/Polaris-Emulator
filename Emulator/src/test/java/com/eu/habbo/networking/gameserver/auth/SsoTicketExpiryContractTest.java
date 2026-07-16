package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoTicketExpiryContractTest {
    @Test
    void builtInIssuersPersistFreshTicketExpiry() throws Exception {
        String endpoints = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

        assertTrue(count(endpoints, "auth_ticket = ?, auth_ticket_expires_at = ?, ip_current = ?") >= 2,
                "password and remember login must persist a fresh expiry with each ticket");
        assertTrue(count(endpoints, "Timestamp ssoExpiry = newSsoTicketExpiry()") >= 2,
                "each built-in issuer must calculate its own fresh deadline");
        assertTrue(endpoints.contains("auth_ticket = '', auth_ticket_expires_at = NULL, online = '0'"),
                "logout must clear both built-in ticket fields");
    }

    @Test
    void legacyCmsTicketsRemainCompatibleWhileNonNullExpiryIsEnforced() throws Exception {
        String combined = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java")
                + read("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java")
                + read("src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java");

        assertTrue(count(combined,
                "auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()") >= 5,
                "all SSO lookups must enforce a supplied expiry without rejecting unchanged legacy CMS tickets");
    }

    @Test
    void expiryIsConfigurableWithoutASecondSessionStore() throws Exception {
        String endpoints = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

        assertTrue(endpoints.contains("login.sso.ticket.ttl.seconds\", 60"),
                "the built-in issuer should use a short configurable TTL");
        assertTrue(!endpoints.contains("users_auth_ticket_sessions"),
                "the proportional fix must not introduce a second SSO session store");
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
