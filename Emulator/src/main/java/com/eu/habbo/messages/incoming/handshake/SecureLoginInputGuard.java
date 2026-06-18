package com.eu.habbo.messages.incoming.handshake;

final class SecureLoginInputGuard {
    static final int MAX_SSO_TICKET_LENGTH = 512;

    private SecureLoginInputGuard() {
    }

    static String normalizeSsoTicket(String ticket) {
        if (ticket == null) {
            return "";
        }

        return ticket.replace(" ", "");
    }

    static boolean isValidSsoTicket(String ticket) {
        return ticket != null && !ticket.isEmpty() && ticket.length() <= MAX_SSO_TICKET_LENGTH;
    }
}
