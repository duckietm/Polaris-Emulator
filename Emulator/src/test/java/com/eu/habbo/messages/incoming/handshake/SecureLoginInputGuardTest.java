package com.eu.habbo.messages.incoming.handshake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureLoginInputGuardTest {

    @Test
    void normalizesNullAndSpacesBeforeAuthentication() {
        assertEquals("", SecureLoginInputGuard.normalizeSsoTicket(null));
        assertEquals("abc123", SecureLoginInputGuard.normalizeSsoTicket(" abc 123 "));
    }

    @Test
    void rejectsMissingOrOversizedTickets() {
        assertFalse(SecureLoginInputGuard.isValidSsoTicket(""));
        assertFalse(SecureLoginInputGuard.isValidSsoTicket("x".repeat(SecureLoginInputGuard.MAX_SSO_TICKET_LENGTH + 1)));
    }

    @Test
    void acceptsTicketWithinBound() {
        assertTrue(SecureLoginInputGuard.isValidSsoTicket("x".repeat(SecureLoginInputGuard.MAX_SSO_TICKET_LENGTH)));
    }
}
