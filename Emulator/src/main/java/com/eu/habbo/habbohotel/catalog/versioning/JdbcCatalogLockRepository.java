package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcCatalogLockRepository implements CatalogLockRepository {
    private static final String SELECT_FOR_UPDATE = "SELECT owner_id, token, expires_at FROM catalog_edit_locks "
            + "WHERE version_id = ? AND catalog_type = ? AND entity_type = ? AND entity_id = ? FOR UPDATE";
    private static final String INSERT = "INSERT INTO catalog_edit_locks "
            + "(version_id, catalog_type, entity_type, entity_id, owner_id, token, expires_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE catalog_edit_locks SET owner_id = ?, token = ?, expires_at = ? "
            + "WHERE version_id = ? AND catalog_type = ? AND entity_type = ? AND entity_id = ?";

    private final DataSource dataSource;

    public JdbcCatalogLockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CatalogLockRecord acquire(
            long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<CatalogLockRecord> existing = findForUpdate(connection, versionId, key);
                if (existing.isPresent()
                        && existing.get().expiresAt().isAfter(now)
                        && existing.get().ownerId() != ownerId) {
                    connection.commit();
                    return existing.get();
                }

                UUID effectiveToken = existing.filter(record -> record.ownerId() == ownerId)
                        .map(CatalogLockRecord::token)
                        .orElse(token);
                CatalogLockRecord acquired = new CatalogLockRecord(versionId, key, ownerId, effectiveToken, expiresAt);
                if (existing.isPresent()) update(connection, acquired);
                else insert(connection, acquired);
                connection.commit();
                return acquired;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public Optional<CatalogLockRecord> renew(
            long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<CatalogLockRecord> existing = findForUpdate(connection, versionId, key);
                if (existing.isEmpty()
                        || !existing.get().expiresAt().isAfter(now)
                        || existing.get().ownerId() != ownerId
                        || !existing.get().token().equals(token)) {
                    connection.commit();
                    return Optional.empty();
                }
                CatalogLockRecord renewed = new CatalogLockRecord(versionId, key, ownerId, token, expiresAt);
                update(connection, renewed);
                connection.commit();
                return Optional.of(renewed);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean release(long versionId, CatalogLockKey key, int ownerId, UUID token) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM catalog_edit_locks WHERE version_id = ? AND catalog_type = ? "
                                + "AND entity_type = ? AND entity_id = ? AND owner_id = ? AND token = ?")) {
            bindKey(statement, versionId, key);
            statement.setInt(5, ownerId);
            statement.setString(6, token.toString());
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public void releaseAll(long versionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM catalog_edit_locks WHERE version_id = ?")) {
            statement.setLong(1, versionId);
            statement.executeUpdate();
        }
    }

    @Override
    public void releaseAll(Connection connection, long versionId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM catalog_edit_locks WHERE version_id = ?")) {
            statement.setLong(1, versionId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<CatalogLockRecord> findActive(
            Connection connection, long versionId, CatalogLockKey key, Instant now) throws SQLException {
        return findForUpdate(connection, versionId, key)
                .filter(record -> record.expiresAt().isAfter(now));
    }

    private static Optional<CatalogLockRecord> findForUpdate(Connection connection, long versionId, CatalogLockKey key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_UPDATE)) {
            bindKey(statement, versionId, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(new CatalogLockRecord(
                        versionId,
                        key,
                        resultSet.getInt("owner_id"),
                        UUID.fromString(resultSet.getString("token")),
                        resultSet.getTimestamp("expires_at").toInstant()));
            }
        }
    }

    private static void insert(Connection connection, CatalogLockRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bindKey(statement, record.versionId(), record.key());
            statement.setInt(5, record.ownerId());
            statement.setString(6, record.token().toString());
            statement.setTimestamp(7, Timestamp.from(record.expiresAt()));
            statement.executeUpdate();
        }
    }

    private static void update(Connection connection, CatalogLockRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setInt(1, record.ownerId());
            statement.setString(2, record.token().toString());
            statement.setTimestamp(3, Timestamp.from(record.expiresAt()));
            statement.setLong(4, record.versionId());
            statement.setString(5, record.key().catalogType().name());
            statement.setString(6, record.key().entityType().name());
            statement.setInt(7, record.key().entityId());
            if (statement.executeUpdate() != 1) throw new SQLException("Catalog lock disappeared during update");
        }
    }

    private static void bindKey(PreparedStatement statement, long versionId, CatalogLockKey key) throws SQLException {
        statement.setLong(1, versionId);
        statement.setString(2, key.catalogType().name());
        statement.setString(3, key.entityType().name());
        statement.setInt(4, key.entityId());
    }
}
