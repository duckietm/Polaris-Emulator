package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CatalogLockService {
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(90);

    private final CatalogLockRepository repository;
    private final Clock clock;

    public CatalogLockService(CatalogLockRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CatalogLockService(CatalogLockRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogLockResult acquire(long versionId, CatalogLockKey key, int ownerId, Duration ttl) {
        validate(versionId, ownerId, ttl);
        Instant now = clock.instant();
        UUID requestedToken = UUID.randomUUID();
        try {
            CatalogLockRecord record = repository.acquire(versionId, key, ownerId, requestedToken, now, now.plus(ttl));
            return record.ownerId() == ownerId ? CatalogLockResult.acquired(record) : CatalogLockResult.denied(record);
        } catch (SQLException exception) {
            throw new CatalogLockPersistenceException(exception);
        }
    }

    public CatalogLockResult renew(long versionId, CatalogLockKey key, int ownerId, UUID token, Duration ttl) {
        validate(versionId, ownerId, ttl);
        Objects.requireNonNull(token, "token");
        Instant now = clock.instant();
        try {
            Optional<CatalogLockRecord> renewed = repository.renew(versionId, key, ownerId, token, now, now.plus(ttl));
            return renewed.map(CatalogLockResult::acquired).orElseGet(CatalogLockResult::missing);
        } catch (SQLException exception) {
            throw new CatalogLockPersistenceException(exception);
        }
    }

    public boolean release(long versionId, CatalogLockKey key, int ownerId, UUID token) {
        validateIdentity(versionId, ownerId);
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        try {
            return repository.release(versionId, key, ownerId, token);
        } catch (SQLException exception) {
            throw new CatalogLockPersistenceException(exception);
        }
    }

    public void releaseAll(long versionId) {
        if (versionId <= 0) throw new IllegalArgumentException("Version ID must be positive");
        try {
            repository.releaseAll(versionId);
        } catch (SQLException exception) {
            throw new CatalogLockPersistenceException(exception);
        }
    }

    private static void validate(long versionId, int ownerId, Duration ttl) {
        validateIdentity(versionId, ownerId);
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Lock TTL must be positive");
    }

    private static void validateIdentity(long versionId, int ownerId) {
        if (versionId <= 0) throw new IllegalArgumentException("Version ID must be positive");
        if (ownerId <= 0) throw new IllegalArgumentException("Owner ID must be positive");
    }
}
