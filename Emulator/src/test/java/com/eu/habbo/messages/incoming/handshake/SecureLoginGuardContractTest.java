package com.eu.habbo.messages.incoming.handshake;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureLoginGuardContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));
    }

    @Test
    void websocketSsoTicketIsLengthBoundedBeforeDatabaseLookup() throws Exception {
        String source = source();

        int maxConstant = source.indexOf("MAX_SSO_TICKET_LENGTH = 128");
        int guard = source.indexOf("sso.isEmpty() || sso.length() > MAX_SSO_TICKET_LENGTH");
        int lookup = source.indexOf("SELECT id FROM users WHERE auth_ticket = ?");

        assertTrue(maxConstant > -1, "Secure login should define the same SSO length cap used by HTTP auth");
        assertTrue(guard > -1, "Secure login must reject missing or oversized SSO tickets");
        assertTrue(guard < lookup, "SSO length must be validated before database lookup");
    }
}
