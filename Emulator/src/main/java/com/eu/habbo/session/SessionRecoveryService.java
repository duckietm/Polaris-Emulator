package com.eu.habbo.session;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRecoveryService {
    private static final int TOKEN_BYTES = 32;

    private final RecoveryTicketStore store;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration recoveryWindow;
    private final Set<Integer> issuedUsers = ConcurrentHashMap.newKeySet();

    SessionRecoveryService(RecoveryTicketStore store, Clock clock, SecureRandom random, Duration recoveryWindow) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.recoveryWindow = Objects.requireNonNull(recoveryWindow);
    }

    public String issue(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        byte[] token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        store.replace(userId, digest(encoded), clock.instant());
        issuedUsers.add(userId);
        return encoded;
    }

    public void checkpoint(Iterable<Integer> userIds) {
        Set<Integer> eligibleUsers = new HashSet<>();
        for (int userId : userIds) {
            if (issuedUsers.contains(userId)) {
                eligibleUsers.add(userId);
            }
        }
        store.activate(eligibleUsers, clock.instant().plus(recoveryWindow));
        issuedUsers.removeAll(eligibleUsers);
    }

    public OptionalInt consume(String token) {
        if (token == null || token.length() < 43 || token.length() > 64) {
            return OptionalInt.empty();
        }
        return store.consume(digest(token), clock.instant());
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
