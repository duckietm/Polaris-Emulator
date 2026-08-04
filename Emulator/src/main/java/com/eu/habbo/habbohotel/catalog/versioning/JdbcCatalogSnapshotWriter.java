package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class JdbcCatalogSnapshotWriter implements CatalogSnapshotWriter {
    static final String UPSERT_PAGE_SQL = "INSERT INTO catalog_version_pages "
            + "(version_id, catalog_type, page_id, parent_id, caption_save, caption, page_layout, icon_color, "
            + "icon_image, min_rank, order_num, visible, enabled, club_only, catalog_mode, vip_only, "
            + "page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, "
            + "page_text_teaser, room_id, includes) VALUES "
            + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), caption_save = VALUES(caption_save), "
            + "caption = VALUES(caption), page_layout = VALUES(page_layout), icon_color = VALUES(icon_color), "
            + "icon_image = VALUES(icon_image), min_rank = VALUES(min_rank), order_num = VALUES(order_num), "
            + "visible = VALUES(visible), enabled = VALUES(enabled), club_only = VALUES(club_only), "
            + "catalog_mode = VALUES(catalog_mode), vip_only = VALUES(vip_only), "
            + "page_headline = VALUES(page_headline), page_teaser = VALUES(page_teaser), "
            + "page_special = VALUES(page_special), page_text1 = VALUES(page_text1), "
            + "page_text2 = VALUES(page_text2), page_text_details = VALUES(page_text_details), "
            + "page_text_teaser = VALUES(page_text_teaser), room_id = VALUES(room_id), "
            + "includes = VALUES(includes)";
    static final String UPSERT_OFFER_SQL = "INSERT INTO catalog_version_offers "
            + "(version_id, catalog_type, offer_id, item_ids, page_id, catalog_name, cost_credits, cost_points, "
            + "points_type, amount, limited_stack, order_number, offer_id_client, song_id, extradata, "
            + "have_offer, club_only) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE item_ids = VALUES(item_ids), page_id = VALUES(page_id), "
            + "catalog_name = VALUES(catalog_name), cost_credits = VALUES(cost_credits), "
            + "cost_points = VALUES(cost_points), points_type = VALUES(points_type), "
            + "amount = VALUES(amount), limited_stack = VALUES(limited_stack), "
            + "order_number = VALUES(order_number), offer_id_client = VALUES(offer_id_client), "
            + "song_id = VALUES(song_id), extradata = VALUES(extradata), "
            + "have_offer = VALUES(have_offer), club_only = VALUES(club_only)";
    static final String DELETE_PAGE_SQL =
            "DELETE FROM catalog_version_pages WHERE version_id = ? AND catalog_type = ? AND page_id = ?";
    static final String DELETE_OFFER_SQL =
            "DELETE FROM catalog_version_offers WHERE version_id = ? AND catalog_type = ? AND offer_id = ?";

    private final Gson gson;

    public JdbcCatalogSnapshotWriter(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public void apply(Connection connection, long versionId, CatalogChangeEntry change) throws SQLException {
        if (change.afterJson() == null) {
            delete(connection, versionId, change);
            return;
        }
        switch (change.entityType()) {
            case PAGE -> {
                CatalogPageSnapshot page = gson.fromJson(change.afterJson(), CatalogPageSnapshot.class);
                if (page.pageId() != change.entityId()) {
                    throw new IllegalArgumentException("Page JSON ID does not match the change entry");
                }
                if (page.catalogType() != change.catalogType()) {
                    throw new IllegalArgumentException("Page JSON catalog type does not match the change entry");
                }
                upsertPage(connection, versionId, page);
            }
            case OFFER -> {
                CatalogOfferSnapshot offer = gson.fromJson(change.afterJson(), CatalogOfferSnapshot.class);
                if (offer.offerId() != change.entityId()) {
                    throw new IllegalArgumentException("Offer JSON ID does not match the change entry");
                }
                if (offer.catalogType() != change.catalogType()) {
                    throw new IllegalArgumentException("Offer JSON catalog type does not match the change entry");
                }
                upsertOffer(connection, versionId, offer);
            }
        }
    }

    @Override
    public void replace(Connection connection, long versionId, CatalogVersionSnapshot source) throws SQLException {
        deleteAll(connection, "DELETE FROM catalog_version_offers WHERE version_id = ?", versionId);
        deleteAll(connection, "DELETE FROM catalog_version_pages WHERE version_id = ?", versionId);
        for (CatalogPageSnapshot page : source.pages()) upsertPage(connection, versionId, page);
        for (CatalogOfferSnapshot offer : source.offers()) upsertOffer(connection, versionId, offer);
    }

    private static void delete(Connection connection, long versionId, CatalogChangeEntry change) throws SQLException {
        String sql = change.entityType() == CatalogEntityType.PAGE ? DELETE_PAGE_SQL : DELETE_OFFER_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, versionId);
            statement.setString(2, change.catalogType().name());
            statement.setInt(3, change.entityId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Catalog entity disappeared before change application");
            }
        }
    }

    private static void upsertPage(Connection connection, long versionId, CatalogPageSnapshot page)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PAGE_SQL)) {
            statement.setLong(1, versionId);
            statement.setString(2, page.catalogType().name());
            statement.setInt(3, page.pageId());
            statement.setInt(4, page.parentId());
            statement.setString(5, page.captionSave());
            statement.setString(6, page.caption());
            statement.setString(7, page.pageLayout());
            statement.setInt(8, page.iconColor());
            statement.setInt(9, page.iconImage());
            statement.setInt(10, page.minRank());
            statement.setInt(11, page.orderNum());
            statement.setBoolean(12, page.visible());
            statement.setBoolean(13, page.enabled());
            statement.setBoolean(14, page.clubOnly());
            statement.setString(15, page.catalogMode());
            statement.setBoolean(16, page.vipOnly());
            statement.setString(17, page.pageHeadline());
            statement.setString(18, page.pageTeaser());
            statement.setString(19, page.pageSpecial());
            statement.setString(20, page.pageText1());
            statement.setString(21, page.pageText2());
            statement.setString(22, page.pageTextDetails());
            statement.setString(23, page.pageTextTeaser());
            statement.setInt(24, page.roomId());
            statement.setString(25, page.includes());
            if (statement.executeUpdate() == 0) throw new SQLException("Catalog page upsert changed no row");
        }
    }

    private static void upsertOffer(Connection connection, long versionId, CatalogOfferSnapshot offer)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_OFFER_SQL)) {
            statement.setLong(1, versionId);
            statement.setString(2, offer.catalogType().name());
            statement.setInt(3, offer.offerId());
            statement.setString(4, offer.itemIds());
            statement.setInt(5, offer.pageId());
            statement.setString(6, offer.catalogName());
            statement.setInt(7, offer.costCredits());
            statement.setInt(8, offer.costPoints());
            statement.setInt(9, offer.pointsType());
            statement.setInt(10, offer.amount());
            statement.setInt(11, offer.limitedStack());
            statement.setInt(12, offer.orderNumber());
            statement.setInt(13, offer.offerIdClient());
            statement.setInt(14, offer.songId());
            statement.setString(15, offer.extradata());
            statement.setBoolean(16, offer.haveOffer());
            statement.setBoolean(17, offer.clubOnly());
            if (statement.executeUpdate() == 0) throw new SQLException("Catalog offer upsert changed no row");
        }
    }

    private static void deleteAll(Connection connection, String sql, long versionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, versionId);
            statement.executeUpdate();
        }
    }
}
