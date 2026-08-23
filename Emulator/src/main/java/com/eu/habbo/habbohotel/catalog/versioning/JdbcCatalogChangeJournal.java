package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores one complete, self-contained audit and undo record per live operation. */
public final class JdbcCatalogChangeJournal implements CatalogChangeJournal {
    static final String LOAD_GROUP_SQL = "SELECT id, revision, actor_id, summary, source, changes_json, created_at "
            + "FROM catalog_manager_history WHERE id = ?";
    static final String LOAD_ENTRIES_SQL = LOAD_GROUP_SQL;
    static final String LATER_CONFLICT_SQL =
            "SELECT changes_json FROM catalog_manager_history " + "WHERE revision > ? ORDER BY revision";
    static final String INSERT_GROUP_SQL = "INSERT INTO catalog_manager_history "
            + "(revision, actor_id, summary, source, changes_json) VALUES (?, ?, ?, ?, ?)";
    static final String INSERT_ENTRY_SQL = INSERT_GROUP_SQL;

    private static final long LIVE_CATALOG_ID = 1;
    private final Gson gson;

    public JdbcCatalogChangeJournal() {
        this(new Gson());
    }

    JdbcCatalogChangeJournal(Gson gson) {
        this.gson = gson;
    }

    @Override
    public CatalogChangeGroup load(Connection connection, long groupId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_GROUP_SQL)) {
            statement.setLong(1, groupId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Catalog Manager history row not found: " + groupId);
                return new CatalogChangeGroup(
                        resultSet.getLong("id"),
                        LIVE_CATALOG_ID,
                        resultSet.getLong("revision"),
                        resultSet.getInt("actor_id"),
                        resultSet.getString("summary"),
                        CatalogChangeSource.valueOf(resultSet.getString("source")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        decodeEntries(resultSet.getString("changes_json")));
            }
        }
    }

    @Override
    public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) throws SQLException {
        Set<String> selected = entityKeys(group.entries());
        try (PreparedStatement statement = connection.prepareStatement(LATER_CONFLICT_SQL)) {
            statement.setLong(1, group.revision());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    for (CatalogChangeEntry entry : decodeEntries(resultSet.getString("changes_json"))) {
                        if (selected.contains(entityKey(entry))) return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void delete(Connection connection, long groupId) {
        throw new UnsupportedOperationException("Catalog Manager history is immutable");
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
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_GROUP_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, revision);
            statement.setInt(2, actorId);
            statement.setString(3, summary);
            statement.setString(4, source.name());
            statement.setString(5, gson.toJson(entries));
            if (statement.executeUpdate() != 1) throw new SQLException("Failed to append Catalog Manager history");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Catalog Manager history ID was not returned");
                return keys.getLong(1);
            }
        }
    }

    private List<CatalogChangeEntry> decodeEntries(String json) throws SQLException {
        try {
            CatalogChangeEntry[] entries = gson.fromJson(json, CatalogChangeEntry[].class);
            return entries == null ? List.of() : List.copyOf(Arrays.asList(entries));
        } catch (RuntimeException exception) {
            throw new SQLException("Catalog Manager history contains invalid changes JSON", exception);
        }
    }

    private static Set<String> entityKeys(List<CatalogChangeEntry> entries) {
        Set<String> keys = new HashSet<>();
        entries.forEach(entry -> keys.add(entityKey(entry)));
        return keys;
    }

    private static String entityKey(CatalogChangeEntry entry) {
        return entry.catalogType() + ":" + entry.entityType() + ":" + entry.entityId();
    }
}
