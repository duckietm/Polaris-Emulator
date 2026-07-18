package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoIssuerExpiryContractTest {
    @Test
    void builtInIssuersRefreshTheExistingExpiryColumn() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        assertTrue(count(source,
                "auth_ticket = ?, auth_ticket_expires_at = ?, ip_current = ?") >= 2,
                "password and remember issuers must persist a fresh expiry with each ticket");
        assertTrue(count(source, "newSsoTicketExpiry()") >= 2,
                "each built-in issuance path must calculate a fresh deadline");
        assertTrue(source.contains("auth_ticket = '', auth_ticket_expires_at = NULL, online = '0'"),
                "logout must clear the built-in ticket expiry alongside the ticket");
    }

    @Test
    void existingCmsNullExpiryCompatibilityRemains() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        assertTrue(source.contains("auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()"),
                "unchanged CMS issuers must retain the existing nullable-expiry behavior");
        assertTrue(!source.contains("users_auth_ticket_sessions"),
                "the proportional fix must not introduce a second SSO session store");
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
