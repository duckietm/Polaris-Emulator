package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public final class JdbcCatalogStudioQueryRepository {
    static final String LOAD_SESSION_SQL =
            "SELECT runtime.active_version_id, runtime.draft_version_id, runtime.updated_at AS active_updated_at, "
                    + "draft.revision, draft.created_at AS draft_created_at "
                    + "FROM catalog_runtime_state runtime "
                    + "JOIN catalog_versions draft ON draft.id = runtime.draft_version_id "
                    + "WHERE runtime.singleton_id = 1";
    static final String LOAD_PENDING_COUNT_SQL =
            "SELECT COUNT(*) AS pending_count FROM catalog_change_groups WHERE version_id = ?";
    static final String LOAD_ACTORS_SQL =
            "SELECT DISTINCT locks.owner_id, users.username FROM catalog_edit_locks locks "
                    + "LEFT JOIN users ON users.id = locks.owner_id "
                    + "WHERE locks.version_id = ? AND locks.expires_at > CURRENT_TIMESTAMP(3) "
                    + "ORDER BY users.username, locks.owner_id";
    static final String LOAD_PUBLISHED_VERSIONS_SQL = "SELECT id, label, published_at FROM catalog_versions "
            + "WHERE status IN ('PUBLISHED', 'ARCHIVED') AND published_at IS NOT NULL "
            + "ORDER BY published_at DESC, id DESC LIMIT 50";
    static final String LOAD_HISTORY_META_SQL =
            "SELECT revision, (SELECT COUNT(*) FROM catalog_change_groups WHERE version_id = ?) AS total_count "
                    + "FROM catalog_versions WHERE id = ?";
    static final String LOAD_HISTORY_GROUPS_SQL =
            "SELECT groups.id, groups.revision, groups.actor_id, users.username AS actor_name, "
                    + "groups.summary, groups.source, groups.created_at "
                    + "FROM catalog_change_groups groups LEFT JOIN users ON users.id = groups.actor_id "
                    + "WHERE groups.version_id = ? ORDER BY groups.revision DESC, groups.id DESC LIMIT ? OFFSET ?";
    static final String LOAD_HISTORY_ENTRIES_SQL =
            "SELECT entity_type, entity_id, operation FROM catalog_change_entries " + "WHERE group_id = ? ORDER BY id";
    static final String LOAD_USERNAME_SQL = "SELECT username FROM users WHERE id = ?";
    static final String LOAD_DRAFT_STATUS_SQL = "SELECT status FROM catalog_versions WHERE id = ?";

    private final DataSource dataSource;

    public JdbcCatalogStudioQueryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public CatalogStudioSessionState loadSession() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LOAD_SESSION_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog Studio runtime state is not initialized");
            long draftVersionId = resultSet.getLong("draft_version_id");
            return new CatalogStudioSessionState(
                    resultSet.getLong("active_version_id"),
                    draftVersionId,
                    resultSet.getLong("revision"),
                    resultSet.getTimestamp("active_updated_at").toInstant(),
                    resultSet.getTimestamp("draft_created_at").toInstant(),
                    loadPendingCount(connection, draftVersionId),
                    loadActors(connection, draftVersionId),
                    false,
                    0,
                    loadPublishedVersions(connection));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Studio session load failed", exception);
        }
    }

    public CatalogHistoryPage loadHistory(long draftVersionId, int offset, int limit) {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection()) {
            long revision;
            int totalCount;
            try (PreparedStatement statement = connection.prepareStatement(LOAD_HISTORY_META_SQL)) {
                statement.setLong(1, draftVersionId);
                statement.setLong(2, draftVersionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) throw new SQLException("Catalog draft not found: " + draftVersionId);
                    revision = resultSet.getLong("revision");
                    totalCount = resultSet.getInt("total_count");
                }
            }
            return new CatalogHistoryPage(
                    draftVersionId,
                    revision,
                    totalCount,
                    loadHistoryGroups(connection, draftVersionId, safeOffset, safeLimit));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Studio history load failed", exception);
        }
    }

    public CatalogVersionSnapshot loadDraftSnapshot(long draftVersionId) {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        try (Connection connection = dataSource.getConnection()) {
            CatalogVersionSnapshot snapshot =
                    new JdbcCatalogVersionRepository().loadSnapshot(connection, draftVersionId);
            if (snapshot.version().status() != CatalogVersionStatus.DRAFT) {
                throw new IllegalArgumentException("Catalog version is not a draft: " + draftVersionId);
            }
            return snapshot;
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Studio draft snapshot load failed", exception);
        }
    }

    public List<CatalogPageSnapshot> loadDraftPages(long draftVersionId) {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LOAD_DRAFT_STATUS_SQL)) {
            statement.setLong(1, draftVersionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Catalog draft not found: " + draftVersionId);
                if (CatalogVersionStatus.valueOf(resultSet.getString("status")) != CatalogVersionStatus.DRAFT) {
                    throw new IllegalArgumentException("Catalog version is not a draft: " + draftVersionId);
                }
            }
            return List.copyOf(JdbcCatalogVersionRepository.loadPages(connection, draftVersionId));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Studio draft page index load failed", exception);
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
            throw new CatalogVersioningException("Catalog Studio username load failed", exception);
        }
    }

    public CatalogChangeGroup loadChangeGroup(long groupId) {
        if (groupId <= 0) throw new IllegalArgumentException("Change group ID must be positive");
        try (Connection connection = dataSource.getConnection()) {
            return new JdbcCatalogChangeJournal().load(connection, groupId);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog Studio change group load failed", exception);
        }
    }

    private static int loadPendingCount(Connection connection, long draftVersionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_PENDING_COUNT_SQL)) {
            statement.setLong(1, draftVersionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return 0;
                return resultSet.getInt("pending_count");
            }
        }
    }

    private static List<CatalogStudioActorState> loadActors(Connection connection, long draftVersionId)
            throws SQLException {
        List<CatalogStudioActorState> actors = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_ACTORS_SQL)) {
            statement.setLong(1, draftVersionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int ownerId = resultSet.getInt("owner_id");
                    String username = resultSet.getString("username");
                    actors.add(new CatalogStudioActorState(ownerId, username == null ? "#" + ownerId : username));
                }
            }
        }
        return List.copyOf(actors);
    }

    private static List<CatalogPublishedVersionState> loadPublishedVersions(Connection connection) throws SQLException {
        List<CatalogPublishedVersionState> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_PUBLISHED_VERSIONS_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp publishedAt = resultSet.getTimestamp("published_at");
                if (publishedAt == null) continue;
                String label = resultSet.getString("label");
                versions.add(new CatalogPublishedVersionState(
                        resultSet.getLong("id"), label == null ? "" : label, publishedAt.toInstant()));
            }
        }
        return List.copyOf(versions);
    }

    private static List<CatalogHistoryGroupState> loadHistoryGroups(
            Connection connection, long draftVersionId, int offset, int limit) throws SQLException {
        List<CatalogHistoryGroupState> groups = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_HISTORY_GROUPS_SQL)) {
            statement.setLong(1, draftVersionId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long groupId = resultSet.getLong("id");
                    int actorId = resultSet.getInt("actor_id");
                    String actorName = resultSet.getString("actor_name");
                    groups.add(new CatalogHistoryGroupState(
                            groupId,
                            resultSet.getLong("revision"),
                            actorId,
                            actorName == null ? "#" + actorId : actorName,
                            resultSet.getString("summary"),
                            CatalogChangeSource.valueOf(resultSet.getString("source")),
                            resultSet.getTimestamp("created_at").toInstant(),
                            loadHistoryEntries(connection, groupId)));
                }
            }
        }
        return List.copyOf(groups);
    }

    private static List<CatalogHistoryEntryState> loadHistoryEntries(Connection connection, long groupId)
            throws SQLException {
        List<CatalogHistoryEntryState> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_HISTORY_ENTRIES_SQL)) {
            statement.setLong(1, groupId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new CatalogHistoryEntryState(
                            CatalogEntityType.valueOf(resultSet.getString("entity_type")),
                            resultSet.getInt("entity_id"),
                            CatalogChangeOperation.valueOf(resultSet.getString("operation"))));
                }
            }
        }
        return List.copyOf(entries);
    }
}
