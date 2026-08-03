package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogLockServiceTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-02T10:00:00Z"));
    private InMemoryLockRepository repository;
    private CatalogLockService service;
    private final CatalogLockKey page = new CatalogLockKey(CatalogEntityType.PAGE, 17);

    @BeforeEach
    void setUp() {
        repository = new InMemoryLockRepository();
        service = new CatalogLockService(repository, clock);
    }

    @Test
    void acquiresAndRenewsTheSameOwnersLock() {
        CatalogLockResult acquired = service.acquire(2, page, 7, Duration.ofSeconds(90));
        clock.advance(Duration.ofSeconds(30));
        CatalogLockResult renewed = service.renew(2, page, 7, acquired.token().orElseThrow(), Duration.ofSeconds(90));

        assertTrue(acquired.acquired());
        assertTrue(renewed.acquired());
        assertEquals(acquired.token(), renewed.token());
        assertEquals(clock.instant().plusSeconds(90), renewed.expiresAt());
    }

    @Test
    void deniesAnotherOwnerUntilTheLockExpires() {
        service.acquire(2, page, 7, Duration.ofSeconds(90));

        CatalogLockResult denied = service.acquire(2, page, 9, Duration.ofSeconds(90));

        assertFalse(denied.acquired());
        assertEquals(7, denied.ownerId());
    }

    @Test
    void allowsAnotherOwnerToTakeOverAnExpiredLock() {
        service.acquire(2, page, 7, Duration.ofSeconds(90));
        clock.advance(Duration.ofSeconds(91));

        CatalogLockResult acquired = service.acquire(2, page, 9, Duration.ofSeconds(90));

        assertTrue(acquired.acquired());
        assertEquals(9, acquired.ownerId());
    }

    @Test
    void releasesOnlyWithTheOwningTokenAndCanClearTheWholeDraft() {
        CatalogLockResult first = service.acquire(2, page, 7, Duration.ofSeconds(90));
        service.acquire(2, new CatalogLockKey(CatalogEntityType.OFFER, 42), 7, Duration.ofSeconds(90));

        assertFalse(service.release(2, page, 7, UUID.randomUUID()));
        assertTrue(service.release(2, page, 7, first.token().orElseThrow()));
        assertEquals(1, repository.size());

        service.releaseAll(2);
        assertEquals(0, repository.size());
    }

    private static final class InMemoryLockRepository implements CatalogLockRepository {
        private final Map<String, CatalogLockRecord> locks = new HashMap<>();

        @Override
        public synchronized CatalogLockRecord acquire(
                long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt) {
            String storageKey = storageKey(versionId, key);
            CatalogLockRecord current = locks.get(storageKey);
            if (current != null && current.expiresAt().isAfter(now) && current.ownerId() != ownerId) {
                return current;
            }
            UUID effectiveToken = current != null && current.ownerId() == ownerId ? current.token() : token;
            CatalogLockRecord next = new CatalogLockRecord(versionId, key, ownerId, effectiveToken, expiresAt);
            locks.put(storageKey, next);
            return next;
        }

        @Override
        public synchronized Optional<CatalogLockRecord> renew(
                long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt) {
            String storageKey = storageKey(versionId, key);
            CatalogLockRecord current = locks.get(storageKey);
            if (current == null
                    || !current.expiresAt().isAfter(now)
                    || current.ownerId() != ownerId
                    || !current.token().equals(token)) return Optional.empty();
            CatalogLockRecord renewed = new CatalogLockRecord(versionId, key, ownerId, token, expiresAt);
            locks.put(storageKey, renewed);
            return Optional.of(renewed);
        }

        @Override
        public synchronized boolean release(long versionId, CatalogLockKey key, int ownerId, UUID token) {
            String storageKey = storageKey(versionId, key);
            CatalogLockRecord current = locks.get(storageKey);
            if (current == null
                    || current.ownerId() != ownerId
                    || !current.token().equals(token)) return false;
            locks.remove(storageKey);
            return true;
        }

        @Override
        public synchronized void releaseAll(long versionId) {
            locks.entrySet().removeIf(entry -> entry.getValue().versionId() == versionId);
        }

        int size() {
            return locks.size();
        }

        private static String storageKey(long versionId, CatalogLockKey key) {
            return versionId + ":" + key.entityType() + ":" + key.entityId();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
