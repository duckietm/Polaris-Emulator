package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class JdbcCatalogChangeJournal implements CatalogChangeJournal {
    static final String LOAD_GROUP_SQL = "SELECT id, version_id, revision, actor_id, summary, source, created_at "
            + "FROM catalog_change_groups WHERE id = ?";
    static final String LOAD_ENTRIES_SQL =
            "SELECT id, entity_type, catalog_type, entity_id, operation, before_json, after_json "
                    + "FROM catalog_change_entries WHERE group_id = ? ORDER BY id";
    static final String LATER_CONFLICT_SQL = "SELECT 1 FROM catalog_change_entries selected "
            + "JOIN catalog_change_entries later_entry "
            + "ON later_entry.entity_type = selected.entity_type "
            + "AND later_entry.catalog_type = selected.catalog_type "
            + "AND later_entry.entity_id = selected.entity_id "
            + "JOIN catalog_change_groups later_group ON later_group.id = later_entry.group_id "
            + "WHERE selected.group_id = ? AND later_group.version_id = ? "
            + "AND later_group.revision > ? LIMIT 1";
    static final String INSERT_GROUP_SQL = "INSERT INTO catalog_change_groups "
            + "(version_id, revision, actor_id, summary, source) VALUES (?, ?, ?, ?, ?)";
    static final String INSERT_ENTRY_SQL = "INSERT INTO catalog_change_entries "
            + "(group_id, entity_type, catalog_type, entity_id, operation, before_json, after_json) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public CatalogChangeGroup load(Connection connection, long groupId) throws SQLException {
        try (PreparedStatement groupStatement = connection.prepareStatement(LOAD_GROUP_SQL)) {
            groupStatement.setLong(1, groupId);
            try (ResultSet resultSet = groupStatement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Catalog change group not found: " + groupId);
                return new CatalogChangeGroup(
                        resultSet.getLong("id"),
                        resultSet.getLong("version_id"),
                        resultSet.getLong("revision"),
                        resultSet.getInt("actor_id"),
                        resultSet.getString("summary"),
                        CatalogChangeSource.valueOf(resultSet.getString("source")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        loadEntries(connection, groupId));
            }
        }
    }

    @Override
    public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LATER_CONFLICT_SQL)) {
            statement.setLong(1, group.id());
            statement.setLong(2, group.versionId());
            statement.setLong(3, group.revision());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public long append(
            Connection connection,
            long versionId,
            long revision,
            int actorId,
            String summary,
            CatalogChangeSource source,
            List<CatalogChangeEntry> entries)
            throws SQLException {
        long groupId;
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_GROUP_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, versionId);
            statement.setLong(2, revision);
            statement.setInt(3, actorId);
            statement.setString(4, summary);
            statement.setString(5, source.name());
            if (statement.executeUpdate() != 1) throw new SQLException("Failed to append catalog change group");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Catalog change group ID was not returned");
                groupId = keys.getLong(1);
            }
        }

        if (!entries.isEmpty()) appendEntries(connection, groupId, entries);
        return groupId;
    }

    private static List<CatalogChangeEntry> loadEntries(Connection connection, long groupId) throws SQLException {
        List<CatalogChangeEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_ENTRIES_SQL)) {
            statement.setLong(1, groupId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new CatalogChangeEntry(
                            resultSet.getLong("id"),
                            CatalogEntityType.valueOf(resultSet.getString("entity_type")),
                            CatalogPageType.valueOf(resultSet.getString("catalog_type")),
                            resultSet.getInt("entity_id"),
                            CatalogChangeOperation.valueOf(resultSet.getString("operation")),
                            resultSet.getString("before_json"),
                            resultSet.getString("after_json")));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static void appendEntries(Connection connection, long groupId, List<CatalogChangeEntry> entries)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ENTRY_SQL)) {
            for (CatalogChangeEntry entry : entries) {
                statement.setLong(1, groupId);
                statement.setString(2, entry.entityType().name());
                statement.setString(3, entry.catalogType().name());
                statement.setInt(4, entry.entityId());
                statement.setString(5, entry.operation().name());
                statement.setString(6, entry.beforeJson());
                statement.setString(7, entry.afterJson());
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            if (results.length != entries.size()) {
                throw new SQLException("Not every catalog change entry was appended");
            }
        }
    }
}
