package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoTicketExpiryContractTest {
    @Test
    void builtInIssuersPersistAnExplicitTicketExpiry() throws Exception {
        String endpoints = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

        assertTrue(count(endpoints, "auth_ticket = ?, auth_ticket_expires_at = ?") >= 2,
                "password and remember login must both persist an SSO expiry");
        assertTrue(count(endpoints, "SsoTicketPolicy.newExpiry()") >= 2,
                "each built-in SSO issuer must calculate a fresh expiry");
        assertTrue(endpoints.contains("auth_ticket = '', auth_ticket_expires_at = NULL"),
                "logout must clear both the ticket and its expiry");
    }

    @Test
    void ticketLookupsDoNotAcceptNullExpiryAsUnlimited() throws Exception {
        String combined = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java")
                + read("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java")
                + read("src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java");

        assertFalse(combined.contains("auth_ticket_expires_at IS NULL"),
                "a missing expiry must not turn an SSO ticket into a permanent credential");
        assertTrue(count(combined, "auth_ticket_expires_at >= NOW()") >= 5,
                "every HTTP and game-login SSO lookup must enforce expiry");
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
