package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Read model for the direct-live Catalog Manager. */
public final class JdbcCatalogStudioQueryRepository {
    static final String LOAD_SESSION_SQL =
            "SELECT revision, updated_at FROM catalog_manager_state " + "WHERE singleton_id = 1";
    static final String LOAD_HISTORY_META_SQL = "SELECT state.revision, "
            + "(SELECT COUNT(*) FROM catalog_manager_history) AS total_count "
            + "FROM catalog_manager_state state WHERE state.singleton_id = 1";
    static final String LOAD_HISTORY_GROUPS_SQL =
            "SELECT history.id, history.revision, history.actor_id, users.username AS actor_name, "
                    + "history.summary, history.source, history.created_at "
                    + "FROM catalog_manager_history history LEFT JOIN users ON users.id = history.actor_id "
                    + "ORDER BY history.revision DESC, history.id DESC LIMIT ? OFFSET ?";
    static final String LOAD_USERNAME_SQL = "SELECT username FROM users WHERE id = ?";

    private static final long LIVE_CATALOG_ID = 1;
    private final DataSource dataSource;

    public JdbcCatalogStudioQueryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public CatalogStudioSessionState loadSession() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LOAD_SESSION_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Manager state is not initialized");
            var updatedAt = resultSet.getTimestamp("updated_at").toInstant();
            return new CatalogStudioSessionState(
                    LIVE_CATALOG_ID,
                    LIVE_CATALOG_ID,
                    resultSet.getLong("revision"),
                    updatedAt,
                    updatedAt,
                    0,
                    List.of(),
                    false,
                    0,
                    List.of());
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Manager session load failed", exception);
        }
    }

    public CatalogHistoryPage loadHistory(long catalogId, int offset, int limit) {
        requireLiveCatalog(catalogId);
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LOAD_HISTORY_META_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Manager state is not initialized");
            return new CatalogHistoryPage(
                    LIVE_CATALOG_ID,
                    resultSet.getLong("revision"),
                    resultSet.getInt("total_count"),
                    loadHistoryGroups(connection, safeOffset, safeLimit));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Manager history load failed", exception);
        }
    }

    public CatalogVersionSnapshot loadLiveSnapshot(long catalogId) {
        requireLiveCatalog(catalogId);
        try (Connection connection = dataSource.getConnection()) {
            JdbcCatalogVersionRepository versions = new JdbcCatalogVersionRepository();
            return new JdbcCatalogLiveSnapshotRepository().loadForRead(connection, versions.loadVersion(connection, 1));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Live catalog snapshot load failed", exception);
        }
    }

    public String username(int userId) {
        if (userId <= 0) return "";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LOAD_USERNAME_SQL)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("username") : "#" + userId;
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Manager username load failed", exception);
        }
    }

    public CatalogChangeGroup loadChangeGroup(long groupId) {
        if (groupId <= 0) throw new IllegalArgumentException("History ID must be positive");
        try (Connection connection = dataSource.getConnection()) {
            return new JdbcCatalogChangeJournal().load(connection, groupId);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Manager history row load failed", exception);
        }
    }

    private static List<CatalogHistoryGroupState> loadHistoryGroups(Connection connection, int offset, int limit)
            throws SQLException {
        List<HistoryRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_HISTORY_GROUPS_SQL)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long id = resultSet.getLong("id");
                    int actorId = resultSet.getInt("actor_id");
                    String actorName = resultSet.getString("actor_name");
                    rows.add(new HistoryRow(
                            id,
                            resultSet.getLong("revision"),
                            actorId,
                            actorName == null ? "#" + actorId : actorName,
                            resultSet.getString("summary"),
                            CatalogChangeSource.valueOf(resultSet.getString("source")),
                            resultSet.getTimestamp("created_at").toInstant()));
                }
            }
        }
        JdbcCatalogChangeJournal journal = new JdbcCatalogChangeJournal();
        List<CatalogHistoryGroupState> groups = new ArrayList<>();
        for (HistoryRow row : rows) {
            CatalogChangeGroup group = journal.load(connection, row.id());
            groups.add(new CatalogHistoryGroupState(
                    row.id(),
                    row.revision(),
                    row.actorId(),
                    row.actorName(),
                    row.summary(),
                    row.source(),
                    row.createdAt(),
                    group.entries().stream()
                            .map(entry -> new CatalogHistoryEntryState(
                                    entry.entityType(), entry.entityId(), entry.operation()))
                            .toList()));
        }
        return List.copyOf(groups);
    }

    private static void requireLiveCatalog(long catalogId) {
        if (catalogId != LIVE_CATALOG_ID) {
            throw new IllegalArgumentException("Catalog Manager live catalog ID must be 1");
        }
    }

    private record HistoryRow(
            long id,
            long revision,
            int actorId,
            String actorName,
            String summary,
            CatalogChangeSource source,
            java.time.Instant createdAt) {}
}
