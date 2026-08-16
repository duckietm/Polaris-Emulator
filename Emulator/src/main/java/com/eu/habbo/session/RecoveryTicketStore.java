package com.eu.habbo.session;

import java.time.Instant;
import java.util.OptionalInt;

interface RecoveryTicketStore {
    void replace(int userId, byte[] tokenHash, Instant issuedAt);

    void activate(Iterable<Integer> userIds, Instant expiresAt);

    OptionalInt consume(byte[] tokenHash, Instant now);
}
