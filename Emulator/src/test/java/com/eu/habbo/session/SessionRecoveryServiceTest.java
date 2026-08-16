package com.eu.habbo.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SessionRecoveryServiceTest {

    @Test
    void issuesOpaqueTokensAndStoresOnlyTheirDigest() {
        FakeStore store = new FakeStore();
        SessionRecoveryService service = service(store);

        String token = service.issue(42);

        assertTrue(token.length() >= 43);
        assertFalse(Arrays.equals(token.getBytes(java.nio.charset.StandardCharsets.UTF_8), store.hashByUser.get(42)));
        assertEquals(32, store.hashByUser.get(42).length);
    }

    @Test
    void recoveryRequiresCheckpointAndConsumesTheTokenOnce() {
        FakeStore store = new FakeStore();
        SessionRecoveryService service = service(store);
        String token = service.issue(42);

        assertTrue(service.consume(token).isEmpty());
        service.checkpoint(java.util.List.of(42));
        assertEquals(OptionalInt.of(42), service.consume(token));
        assertTrue(service.consume(token).isEmpty());
    }

    private static SessionRecoveryService service(FakeStore store) {
        return new SessionRecoveryService(
                store,
                Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                Duration.ofMinutes(2));
    }

    private static final class FakeStore implements RecoveryTicketStore {
        private final Map<Integer, byte[]> hashByUser = new HashMap<>();
        private byte[] activeHash;
        private boolean consumed;

        @Override
        public void replace(int userId, byte[] tokenHash, Instant issuedAt) {
            hashByUser.put(userId, tokenHash.clone());
        }

        @Override
        public void activate(Iterable<Integer> userIds, Instant expiresAt) {
            for (int userId : userIds) {
                activeHash = hashByUser.get(userId);
            }
        }

        @Override
        public OptionalInt consume(byte[] tokenHash, Instant now) {
            if (!consumed && activeHash != null && Arrays.equals(activeHash, tokenHash)) {
                consumed = true;
                return OptionalInt.of(42);
            }
            return OptionalInt.empty();
        }
    }
}
