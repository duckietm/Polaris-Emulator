package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcCatalogVersionRepository implements CatalogVersionRepository {
    static final String LOCK_RUNTIME_STATE_SQL = "SELECT active_version_id, draft_version_id, updated_at "
            + "FROM catalog_runtime_state WHERE singleton_id = 1 FOR UPDATE";
    static final String NEXT_PAGE_ID_SQL =
            "SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'PAGE' " + "AND catalog_type = ? FOR UPDATE";
    static final String NEXT_OFFER_ID_SQL =
            "SELECT next_id FROM catalog_id_sequences WHERE entity_type = 'OFFER' " + "AND catalog_type = ? FOR UPDATE";
    static final String INCREMENT_REVISION_SQL =
            "UPDATE catalog_versions SET revision = revision + 1 WHERE id = ? AND revision = ?";
    static final String UPDATE_NEXT_ID_SQL =
            "UPDATE catalog_id_sequences SET next_id = ? WHERE entity_type = ? AND catalog_type = ?";

    static final String LOAD_VERSION_SQL =
            "SELECT id, status, based_on_version_id, revision, label, created_by, created_at, "
                    + "published_by, published_at FROM catalog_versions WHERE id = ?";
    static final String LOAD_PAGES_SQL =
            "SELECT catalog_type, page_id, parent_id, caption_save, caption, page_layout, icon_color, icon_image, "
                    + "min_rank, order_num, visible, enabled, club_only, catalog_mode, vip_only, "
                    + "page_headline, page_teaser, page_special, page_text1, page_text2, "
                    + "page_text_details, page_text_teaser, room_id, includes "
                    + "FROM catalog_version_pages WHERE version_id = ? ORDER BY catalog_type, page_id";
    static final String LOAD_OFFERS_SQL =
            "SELECT catalog_type, offer_id, item_ids, page_id, catalog_name, cost_credits, cost_points, points_type, "
                    + "amount, limited_stack, order_number, offer_id_client, song_id, extradata, "
                    + "have_offer, club_only FROM catalog_version_offers "
                    + "WHERE version_id = ? ORDER BY catalog_type, offer_id";
    static final String LOAD_PAGE_SQL =
            "SELECT catalog_type, page_id, parent_id, caption_save, caption, page_layout, icon_color, icon_image, "
                    + "min_rank, order_num, visible, enabled, club_only, catalog_mode, vip_only, "
                    + "page_headline, page_teaser, page_special, page_text1, page_text2, "
                    + "page_text_details, page_text_teaser, room_id, includes "
                    + "FROM catalog_version_pages WHERE version_id = ? AND catalog_type = ? AND page_id = ?";
    static final String LOAD_OFFER_SQL =
            "SELECT catalog_type, offer_id, item_ids, page_id, catalog_name, cost_credits, cost_points, points_type, "
                    + "amount, limited_stack, order_number, offer_id_client, song_id, extradata, "
                    + "have_offer, club_only FROM catalog_version_offers "
                    + "WHERE version_id = ? AND catalog_type = ? AND offer_id = ?";
    private static final String CREATE_DRAFT_SQL = "INSERT INTO catalog_versions "
            + "(status, based_on_version_id, revision, label, created_by) "
            + "VALUES ('DRAFT', ?, 0, ?, ?)";
    private static final String CLONE_PAGES_SQL = "INSERT INTO catalog_version_pages "
            + "SELECT ?, catalog_type, page_id, parent_id, caption_save, caption, page_layout, icon_color, "
            + "icon_image, min_rank, order_num, visible, enabled, club_only, catalog_mode, "
            + "vip_only, page_headline, page_teaser, page_special, page_text1, page_text2, "
            + "page_text_details, page_text_teaser, room_id, includes "
            + "FROM catalog_version_pages WHERE version_id = ?";
    private static final String CLONE_OFFERS_SQL = "INSERT INTO catalog_version_offers "
            + "SELECT ?, catalog_type, offer_id, item_ids, page_id, catalog_name, cost_credits, cost_points, "
            + "points_type, amount, limited_stack, order_number, offer_id_client, song_id, "
            + "extradata, have_offer, club_only FROM catalog_version_offers WHERE version_id = ?";

    @Override
    public CatalogRuntimeState lockRuntimeState(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_RUNTIME_STATE_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) throw new SQLException("Catalog runtime state is not initialized");
            return new CatalogRuntimeState(
                    resultSet.getLong("active_version_id"),
                    resultSet.getLong("draft_version_id"),
                    resultSet.getTimestamp("updated_at").toInstant());
        }
    }

    @Override
    public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) throws SQLException {
        CatalogVersion version = loadVersion(connection, versionId);
        List<CatalogPageSnapshot> pages = loadPages(connection, versionId);
        List<CatalogOfferSnapshot> offers = loadOffers(connection, versionId);
        return new CatalogVersionSnapshot(version, pages, offers);
    }

    @Override
    public long cloneAsDraft(Connection connection, long sourceVersionId, int actorId, String label)
            throws SQLException {
        long draftVersionId;
        try (PreparedStatement statement =
                connection.prepareStatement(CREATE_DRAFT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sourceVersionId);
            statement.setString(2, label);
            statement.setInt(3, actorId);
            if (statement.executeUpdate() != 1) throw new SQLException("Failed to create catalog draft");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Catalog draft ID was not returned");
                draftVersionId = keys.getLong(1);
            }
        }

        cloneRows(connection, CLONE_PAGES_SQL, draftVersionId, sourceVersionId);
        cloneRows(connection, CLONE_OFFERS_SQL, draftVersionId, sourceVersionId);
        return draftVersionId;
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
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_REVISION_SQL)) {
            statement.setLong(1, versionId);
            statement.setLong(2, expectedRevision);
            if (statement.executeUpdate() != 1) {
                throw new CatalogConcurrentModificationException(versionId, expectedRevision);
            }
        }
        return expectedRevision + 1;
    }

    @Override
    public void archiveVersion(Connection connection, long versionId) throws SQLException {
        updateVersionStatus(connection, "UPDATE catalog_versions SET status = 'ARCHIVED' WHERE id = ?", versionId, 0);
    }

    @Override
    public void markPublished(Connection connection, long versionId, int actorId) throws SQLException {
        updateVersionStatus(
                connection,
                "UPDATE catalog_versions SET status = 'PUBLISHED', published_by = ?, "
                        + "published_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND status = 'DRAFT'",
                versionId,
                actorId);
    }

    @Override
    public void updateRuntimePointers(Connection connection, long activeVersionId, long draftVersionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE catalog_runtime_state SET active_version_id = ?, draft_version_id = ? "
                        + "WHERE singleton_id = 1")) {
            statement.setLong(1, activeVersionId);
            statement.setLong(2, draftVersionId);
            if (statement.executeUpdate() != 1) throw new SQLException("Catalog runtime state is not initialized");
        }
    }

    @Override
    public CatalogVersion loadVersion(Connection connection, long versionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_VERSION_SQL)) {
            statement.setLong(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Catalog version not found: " + versionId);
                Timestamp publishedAt = resultSet.getTimestamp("published_at");
                return new CatalogVersion(
                        resultSet.getLong("id"),
                        CatalogVersionStatus.valueOf(resultSet.getString("status")),
                        nullableLong(resultSet, "based_on_version_id"),
                        resultSet.getLong("revision"),
                        resultSet.getString("label"),
                        resultSet.getInt("created_by"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        nullableInteger(resultSet, "published_by"),
                        publishedAt == null ? null : publishedAt.toInstant());
            }
        }
    }

    @Override
    public Optional<CatalogPageSnapshot> loadPage(
            Connection connection, long versionId, CatalogPageType catalogType, int pageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_PAGE_SQL)) {
            statement.setLong(1, versionId);
            statement.setString(2, catalogType.name());
            statement.setInt(3, pageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapPage(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CatalogOfferSnapshot> loadOffer(
            Connection connection, long versionId, CatalogPageType catalogType, int offerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_OFFER_SQL)) {
            statement.setLong(1, versionId);
            statement.setString(2, catalogType.name());
            statement.setInt(3, offerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapOffer(resultSet)) : Optional.empty();
            }
        }
    }

    static List<CatalogPageSnapshot> loadPages(Connection connection, long versionId) throws SQLException {
        List<CatalogPageSnapshot> pages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_PAGES_SQL)) {
            statement.setLong(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) pages.add(mapPage(resultSet));
            }
        }
        return pages;
    }

    private static List<CatalogOfferSnapshot> loadOffers(Connection connection, long versionId) throws SQLException {
        List<CatalogOfferSnapshot> offers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_OFFERS_SQL)) {
            statement.setLong(1, versionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) offers.add(mapOffer(resultSet));
            }
        }
        return offers;
    }

    private static CatalogPageSnapshot mapPage(ResultSet resultSet) throws SQLException {
        return new CatalogPageSnapshot(
                CatalogPageType.valueOf(resultSet.getString("catalog_type")),
                resultSet.getInt("page_id"),
                resultSet.getInt("parent_id"),
                resultSet.getString("caption_save"),
                resultSet.getString("caption"),
                resultSet.getString("page_layout"),
                resultSet.getInt("icon_color"),
                resultSet.getInt("icon_image"),
                resultSet.getInt("min_rank"),
                resultSet.getInt("order_num"),
                resultSet.getBoolean("visible"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("club_only"),
                resultSet.getString("catalog_mode"),
                resultSet.getBoolean("vip_only"),
                resultSet.getString("page_headline"),
                resultSet.getString("page_teaser"),
                resultSet.getString("page_special"),
                resultSet.getString("page_text1"),
                resultSet.getString("page_text2"),
                resultSet.getString("page_text_details"),
                resultSet.getString("page_text_teaser"),
                resultSet.getInt("room_id"),
                resultSet.getString("includes"));
    }

    private static CatalogOfferSnapshot mapOffer(ResultSet resultSet) throws SQLException {
        return new CatalogOfferSnapshot(
                CatalogPageType.valueOf(resultSet.getString("catalog_type")),
                resultSet.getInt("offer_id"),
                resultSet.getString("item_ids"),
                resultSet.getInt("page_id"),
                resultSet.getString("catalog_name"),
                resultSet.getInt("cost_credits"),
                resultSet.getInt("cost_points"),
                resultSet.getInt("points_type"),
                resultSet.getInt("amount"),
                resultSet.getInt("limited_stack"),
                resultSet.getInt("order_number"),
                resultSet.getInt("offer_id_client"),
                resultSet.getInt("song_id"),
                resultSet.getString("extradata"),
                resultSet.getBoolean("have_offer"),
                resultSet.getBoolean("club_only"));
    }

    private static void cloneRows(Connection connection, String sql, long draftVersionId, long sourceVersionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, draftVersionId);
            statement.setLong(2, sourceVersionId);
            statement.executeUpdate();
        }
    }

    private static long nextStableId(
            Connection connection, CatalogEntityType entityType, CatalogPageType catalogType, String selectSql)
            throws SQLException {
        if (catalogType == null || catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("Stable IDs require one physical catalog type");
        }
        long allocatedId;
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, catalogType.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Stable catalog ID query returned no row");
                allocatedId = resultSet.getLong(1);
            }
        }
        long nextId = Math.addExact(allocatedId, 1L);
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_NEXT_ID_SQL)) {
            statement.setLong(1, nextId);
            statement.setString(2, entityType.name());
            statement.setString(3, catalogType.name());
            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Stable catalog ID sequence is not initialized: " + catalogType + " " + entityType);
            }
        }
        return allocatedId;
    }

    private static void updateVersionStatus(Connection connection, String sql, long versionId, int actorId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int versionParameter;
            if (actorId > 0) {
                statement.setInt(1, actorId);
                versionParameter = 2;
            } else {
                versionParameter = 1;
            }
            statement.setLong(versionParameter, versionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Catalog version state change failed: " + versionId);
            }
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
