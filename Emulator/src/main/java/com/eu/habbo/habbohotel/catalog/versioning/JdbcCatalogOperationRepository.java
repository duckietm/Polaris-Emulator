package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Idempotency metadata is part of the same Manager history row. */
public final class JdbcCatalogOperationRepository implements CatalogOperationRepository {
    static final String SELECT_FOR_UPDATE = "SELECT id, revision, source, request_fingerprint "
            + "FROM catalog_manager_history WHERE operation_id = ? AND actor_id = ? FOR UPDATE";
    static final String ATTACH_TO_GROUP = "UPDATE catalog_manager_history "
            + "SET operation_id = ?, request_fingerprint = ? WHERE id = ? AND actor_id = ? AND operation_id IS NULL";
    static final String ATTACH_TO_REVISION = "UPDATE catalog_manager_history "
            + "SET operation_id = ?, request_fingerprint = ? "
            + "WHERE revision = ? AND actor_id = ? AND operation_id IS NULL";

    private static final long LIVE_CATALOG_ID = 1;

    @Override
    public Optional<CatalogOperationRecord> findForUpdate(Connection connection, String operationId, int actorId)
            throws SQLException {
        CatalogOperationRecord.validateIdentity(operationId, actorId);
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_UPDATE)) {
            statement.setString(1, operationId);
            statement.setInt(2, actorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                long historyId = resultSet.getLong("id");
                return Optional.of(new CatalogOperationRecord(
                        operationId,
                        actorId,
                        LIVE_CATALOG_ID,
                        CatalogChangeSource.valueOf(resultSet.getString("source")),
                        resultSet.getLong("revision"),
                        resultSet.getString("request_fingerprint"),
                        null,
                        null,
                        null,
                        null,
                        historyId,
                        null));
            }
        }
    }

    @Override
    public void insert(Connection connection, CatalogOperationRecord record) throws SQLException {
        String sql = record.historyGroupId() == null ? ATTACH_TO_REVISION : ATTACH_TO_GROUP;
        long target = record.historyGroupId() == null ? record.resultRevision() : record.historyGroupId();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.operationId());
            statement.setString(2, record.requestFingerprint());
            statement.setLong(3, target);
            statement.setInt(4, record.actorId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Catalog operation could not be attached to its history row");
            }
        }
    }
}
