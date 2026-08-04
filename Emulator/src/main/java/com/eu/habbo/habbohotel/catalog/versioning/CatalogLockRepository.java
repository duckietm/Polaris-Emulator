package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CatalogLockRepository {
    default Optional<CatalogLockRecord> findActive(
            Connection connection, long versionId, CatalogLockKey key, Instant now) throws SQLException {
        throw new UnsupportedOperationException("Active lock lookup is not implemented");
    }

    CatalogLockRecord acquire(
            long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt)
            throws SQLException;

    Optional<CatalogLockRecord> renew(
            long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt)
            throws SQLException;

    boolean release(long versionId, CatalogLockKey key, int ownerId, UUID token) throws SQLException;

    default void releaseAll(Connection connection, long versionId) throws SQLException {
        releaseAll(versionId);
    }

    void releaseAll(long versionId) throws SQLException;
}
