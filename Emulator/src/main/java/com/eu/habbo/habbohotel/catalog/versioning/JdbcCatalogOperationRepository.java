package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

public final class JdbcCatalogOperationRepository implements CatalogOperationRepository {
    private static final String SELECT_FOR_UPDATE =
            "SELECT operation_id, actor_id, version_id, source, result_revision, "
                    + "request_fingerprint, action, entity_type, catalog_type, entity_id, history_group_id, result_json "
                    + "FROM catalog_operations WHERE operation_id = ? AND actor_id = ? FOR UPDATE";
    private static final String INSERT = "INSERT INTO catalog_operations "
            + "(operation_id, actor_id, version_id, source, result_revision, request_fingerprint, action, entity_type, "
            + "catalog_type, entity_id, history_group_id, result_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Optional<CatalogOperationRecord> findForUpdate(Connection connection, String operationId, int actorId)
            throws SQLException {
        CatalogOperationRecord.validateIdentity(operationId, actorId);
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_UPDATE)) {
            statement.setString(1, operationId);
            statement.setInt(2, actorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                return Optional.of(new CatalogOperationRecord(
                        resultSet.getString("operation_id"),
                        resultSet.getInt("actor_id"),
                        resultSet.getLong("version_id"),
                        CatalogChangeSource.valueOf(resultSet.getString("source")),
                        resultSet.getLong("result_revision"),
                        resultSet.getString("request_fingerprint"),
                        nullableEnum(resultSet, "action", CatalogChangeOperation.class),
                        nullableEnum(resultSet, "entity_type", CatalogEntityType.class),
                        nullableEnum(resultSet, "catalog_type", CatalogPageType.class),
                        resultSet.getObject("entity_id", Integer.class),
                        resultSet.getObject("history_group_id", Long.class),
                        resultSet.getString("result_json")));
            }
        }
    }

    @Override
    public void insert(Connection connection, CatalogOperationRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, record.operationId());
            statement.setInt(2, record.actorId());
            statement.setLong(3, record.versionId());
            statement.setString(4, record.source().name());
            statement.setLong(5, record.resultRevision());
            setNullableString(statement, 6, record.requestFingerprint());
            setNullableEnum(statement, 7, record.action());
            setNullableEnum(statement, 8, record.entityType());
            setNullableEnum(statement, 9, record.catalogType());
            setNullableInteger(statement, 10, record.entityId());
            setNullableLong(statement, 11, record.historyGroupId());
            setNullableString(statement, 12, record.resultJson());
            if (statement.executeUpdate() != 1) throw new SQLException("Catalog operation was not persisted");
        }
    }

    private static <E extends Enum<E>> E nullableEnum(ResultSet resultSet, String column, Class<E> enumType)
            throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void setNullableEnum(PreparedStatement statement, int index, Enum<?> value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value.name());
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }
}
