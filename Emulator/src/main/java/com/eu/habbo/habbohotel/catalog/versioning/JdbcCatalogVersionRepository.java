package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/** Compatibility facade over the direct-live Manager state and stable ID sequences. */
public final class JdbcCatalogVersionRepository implements CatalogVersionRepository {
    static final String LOCK_RUNTIME_STATE_SQL =
            "SELECT revision, updated_at FROM catalog_manager_state " + "WHERE singleton_id = 1 FOR UPDATE";
    static final String NEXT_PAGE_ID_SQL =
            "SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'PAGE' AND catalog_type = ? FOR UPDATE";
    static final String NEXT_OFFER_ID_SQL =
            "SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'OFFER' AND catalog_type = ? FOR UPDATE";
    static final String INCREMENT_REVISION_SQL =
            "UPDATE catalog_manager_state " + "SET revision = revision + 1 WHERE singleton_id = 1 AND revision = ?";
    static final String UPDATE_NEXT_ID_SQL =
            "UPDATE catalog_id_sequences SET next_id = ? WHERE entity_type = ? AND catalog_type = ?";
    static final String LOAD_VERSION_SQL =
            "SELECT revision, updated_at FROM catalog_manager_state WHERE singleton_id = 1";
    static final String READ_RUNTIME_STATE_SQL = LOAD_VERSION_SQL;

    @Override
    public CatalogRuntimeState lockRuntimeState(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_RUNTIME_STATE_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Manager state is not initialized");
            return new CatalogRuntimeState(
                    1, 2, resultSet.getTimestamp("updated_at").toInstant());
        }
    }

    @Override
    public CatalogRuntimeState readRuntimeState(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(READ_RUNTIME_STATE_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Manager state is not initialized");
            return new CatalogRuntimeState(
                    1, 2, resultSet.getTimestamp("updated_at").toInstant());
        }
    }

    @Override
    public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) throws SQLException {
        return new JdbcCatalogLiveSnapshotRepository().load(connection, loadVersion(connection, versionId));
    }

    @Override
    public CatalogVersion loadVersion(Connection connection, long versionId) throws SQLException {
        if (versionId != 1) throw new SQLException("Live Catalog Manager ID must be 1");
        try (PreparedStatement statement = connection.prepareStatement(LOAD_VERSION_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Manager state is not initialized");
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            return new CatalogVersion(
                    1,
                    CatalogVersionStatus.PUBLISHED,
                    null,
                    resultSet.getLong("revision"),
                    "Live catalog",
                    0,
                    updatedAt.toInstant(),
                    null,
                    updatedAt.toInstant());
        }
    }

    @Override
    public long nextPageId(Connection connection) throws SQLException {
        return nextPageId(connection, CatalogPageType.NORMAL);
    }

    @Override
    public long nextPageId(Connection connection, CatalogPageType catalogType) throws SQLException {
        return nextStableId(connection, CatalogEntityType.PAGE, catalogType, NEXT_PAGE_ID_SQL);
    }

    @Override
    public long nextOfferId(Connection connection) throws SQLException {
        return nextOfferId(connection, CatalogPageType.NORMAL);
    }

    @Override
    public long nextOfferId(Connection connection, CatalogPageType catalogType) throws SQLException {
        return nextStableId(connection, CatalogEntityType.OFFER, catalogType, NEXT_OFFER_ID_SQL);
    }

    @Override
    public long incrementRevision(Connection connection, long versionId, long expectedRevision) throws SQLException {
        if (versionId != 1) throw new SQLException("Live Catalog Manager ID must be 1");
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_REVISION_SQL)) {
            statement.setLong(1, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new CatalogConcurrentModificationException(versionId, expectedRevision);
            }
        }
        return expectedRevision + 1;
    }

    static boolean readStrictBoolean(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        if (resultSet.wasNull()) return false;
        if (value != 0 && value != 1) {
            throw new SQLException("Invalid boolean value " + value + " in column " + column);
        }
        return value == 1;
    }

    private static long nextStableId(
            Connection connection, CatalogEntityType entityType, CatalogPageType catalogType, String selectSql)
            throws SQLException {
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("A stable ID cannot target both catalogs");
        }
        long nextId;
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, catalogType.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Catalog ID sequence is not initialized: " + entityType + "/" + catalogType);
                }
                nextId = resultSet.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_NEXT_ID_SQL)) {
            statement.setLong(1, nextId + 1);
            statement.setString(2, entityType.name());
            statement.setString(3, catalogType.name());
            if (statement.executeUpdate() != 1) throw new SQLException("Catalog ID sequence update failed");
        }
        return nextId;
    }
}
