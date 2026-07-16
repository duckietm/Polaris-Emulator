package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;

import java.sql.Timestamp;
import java.time.Instant;

final class SsoTicketPolicy {
    static final int DEFAULT_TTL_SECONDS = 60;

    private SsoTicketPolicy() {
    }

    static Timestamp newExpiry() {
        int ttlSeconds = Math.max(1,
                Emulator.getConfig().getInt("login.sso.ticket.ttl.seconds", DEFAULT_TTL_SECONDS));
        return Timestamp.from(Instant.now().plusSeconds(ttlSeconds));
    }
}
