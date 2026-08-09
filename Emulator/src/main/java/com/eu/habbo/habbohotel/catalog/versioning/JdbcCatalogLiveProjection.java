package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class JdbcCatalogLiveProjection implements CatalogLiveProjection {
    static final String UPSERT_PAGE_SQL = "INSERT INTO catalog_pages "
            + "(id, parent_id, caption_save, caption, page_layout, icon_color, icon_image, min_rank, "
            + "order_num, visible, enabled, club_only, catalog_mode, vip_only, page_headline, "
            + "page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, "
            + "room_id, includes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), caption_save = VALUES(caption_save), "
            + "caption = VALUES(caption), page_layout = VALUES(page_layout), icon_color = VALUES(icon_color), "
            + "icon_image = VALUES(icon_image), min_rank = VALUES(min_rank), order_num = VALUES(order_num), "
            + "visible = VALUES(visible), enabled = VALUES(enabled), club_only = VALUES(club_only), "
            + "catalog_mode = VALUES(catalog_mode), vip_only = VALUES(vip_only), "
            + "page_headline = VALUES(page_headline), page_teaser = VALUES(page_teaser), "
            + "page_special = VALUES(page_special), page_text1 = VALUES(page_text1), "
            + "page_text2 = VALUES(page_text2), page_text_details = VALUES(page_text_details), "
            + "page_text_teaser = VALUES(page_text_teaser), room_id = VALUES(room_id), includes = VALUES(includes)";
    static final String UPSERT_OFFER_SQL = "INSERT INTO catalog_items "
            + "(id, item_ids, page_id, catalog_name, cost_credits, cost_points, points_type, amount, "
            + "limited_stack, limited_sells, order_number, offer_id, song_id, extradata, have_offer, club_only) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE item_ids = VALUES(item_ids), page_id = VALUES(page_id), "
            + "catalog_name = VALUES(catalog_name), cost_credits = VALUES(cost_credits), "
            + "cost_points = VALUES(cost_points), points_type = VALUES(points_type), amount = VALUES(amount), "
            + "limited_stack = VALUES(limited_stack), order_number = VALUES(order_number), "
            + "offer_id = VALUES(offer_id), song_id = VALUES(song_id), extradata = VALUES(extradata), "
            + "have_offer = VALUES(have_offer), club_only = VALUES(club_only)";
    static final String UPSERT_BUILDER_PAGE_SQL = "INSERT INTO catalog_pages_bc "
            + "(id, parent_id, caption, page_layout, icon_color, icon_image, order_num, visible, enabled, "
            + "page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, "
            + "page_text_teaser) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), caption = VALUES(caption), "
            + "page_layout = VALUES(page_layout), icon_color = VALUES(icon_color), "
            + "icon_image = VALUES(icon_image), order_num = VALUES(order_num), visible = VALUES(visible), "
            + "enabled = VALUES(enabled), page_headline = VALUES(page_headline), "
            + "page_teaser = VALUES(page_teaser), page_special = VALUES(page_special), "
            + "page_text1 = VALUES(page_text1), page_text2 = VALUES(page_text2), "
            + "page_text_details = VALUES(page_text_details), page_text_teaser = VALUES(page_text_teaser)";
    static final String UPSERT_BUILDER_OFFER_SQL =
            "INSERT INTO catalog_items_bc (id, item_ids, page_id, catalog_name, order_number, extradata) "
                    + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE item_ids = VALUES(item_ids), "
                    + "page_id = VALUES(page_id), catalog_name = VALUES(catalog_name), "
                    + "order_number = VALUES(order_number), extradata = VALUES(extradata)";
    static final String DELETE_OFFERS_SQL =
            "DELETE FROM catalog_items WHERE NOT EXISTS (SELECT 1 FROM catalog_version_offers version_offer "
                    + "WHERE version_offer.version_id = ? AND version_offer.catalog_type = 'NORMAL' "
                    + "AND version_offer.offer_id = catalog_items.id "
                    + "AND EXISTS (SELECT 1 FROM catalog_version_pages version_page "
                    + "WHERE version_page.version_id = version_offer.version_id "
                    + "AND version_page.catalog_type = version_offer.catalog_type "
                    + "AND version_page.page_id = version_offer.page_id))";
    static final String DELETE_PAGES_SQL =
            "DELETE FROM catalog_pages WHERE NOT EXISTS (SELECT 1 FROM catalog_version_pages version_page "
                    + "WHERE version_page.version_id = ? AND version_page.catalog_type = 'NORMAL' "
                    + "AND version_page.page_id = catalog_pages.id)";
    static final String DELETE_BUILDER_OFFERS_SQL =
            "DELETE FROM catalog_items_bc WHERE NOT EXISTS (SELECT 1 FROM catalog_version_offers version_offer "
                    + "WHERE version_offer.version_id = ? AND version_offer.catalog_type = 'BUILDER' "
                    + "AND version_offer.offer_id = catalog_items_bc.id "
                    + "AND EXISTS (SELECT 1 FROM catalog_version_pages version_page "
                    + "WHERE version_page.version_id = version_offer.version_id "
                    + "AND version_page.catalog_type = version_offer.catalog_type "
                    + "AND version_page.page_id = version_offer.page_id))";
    static final String DELETE_BUILDER_PAGES_SQL =
            "DELETE FROM catalog_pages_bc WHERE NOT EXISTS (SELECT 1 FROM catalog_version_pages version_page "
                    + "WHERE version_page.version_id = ? AND version_page.catalog_type = 'BUILDER' "
                    + "AND version_page.page_id = catalog_pages_bc.id)";

    @Override
    public void replace(Connection connection, CatalogVersionSnapshot snapshot) throws SQLException {
        upsertPages(connection, snapshot);
        upsertOffers(connection, snapshot);
        upsertBuilderPages(connection, snapshot);
        upsertBuilderOffers(connection, snapshot);
        deleteAbsent(connection, DELETE_OFFERS_SQL, snapshot.version().id());
        deleteAbsent(connection, DELETE_PAGES_SQL, snapshot.version().id());
        deleteAbsent(connection, DELETE_BUILDER_OFFERS_SQL, snapshot.version().id());
        deleteAbsent(connection, DELETE_BUILDER_PAGES_SQL, snapshot.version().id());
    }

    private static void upsertPages(Connection connection, CatalogVersionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PAGE_SQL)) {
            ArrayList<CatalogPageSnapshot> pages = new ArrayList<>(snapshot.pages());
            pages.sort(Comparator.comparingInt(page -> depth(snapshot, page)));
            for (CatalogPageSnapshot page : pages) {
                if (page.catalogType() == CatalogPageType.NORMAL) addPageBatch(statement, page);
            }
            statement.executeBatch();
        }
    }

    private static void addPageBatch(PreparedStatement statement, CatalogPageSnapshot page) throws SQLException {
        statement.setInt(1, page.pageId());
        statement.setInt(2, page.parentId());
        statement.setString(3, page.captionSave());
        statement.setString(4, page.caption());
        statement.setString(5, page.pageLayout());
        statement.setInt(6, page.iconColor());
        statement.setInt(7, page.iconImage());
        statement.setInt(8, page.minRank());
        statement.setInt(9, page.orderNum());
        setLegacyEnumBoolean(statement, 10, page.visible());
        setLegacyEnumBoolean(statement, 11, page.enabled());
        setLegacyEnumBoolean(statement, 12, page.clubOnly());
        statement.setString(13, page.catalogMode());
        setLegacyEnumBoolean(statement, 14, page.vipOnly());
        statement.setString(15, page.pageHeadline());
        statement.setString(16, page.pageTeaser());
        statement.setString(17, page.pageSpecial());
        statement.setString(18, page.pageText1());
        statement.setString(19, page.pageText2());
        statement.setString(20, page.pageTextDetails());
        statement.setString(21, page.pageTextTeaser());
        statement.setInt(22, page.roomId());
        statement.setString(23, page.includes());
        statement.addBatch();
    }

    private static void upsertOffers(Connection connection, CatalogVersionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_OFFER_SQL)) {
            try {
                for (CatalogOfferSnapshot offer : snapshot.offers()) {
                    if (offer.catalogType() != CatalogPageType.NORMAL) continue;
                    if (snapshot.page(offer.catalogType(), offer.pageId()).isEmpty()) continue;
                    statement.setInt(1, offer.offerId());
                    statement.setString(2, offer.itemIds());
                    statement.setInt(3, offer.pageId());
                    statement.setString(4, offer.catalogName());
                    statement.setInt(5, offer.costCredits());
                    statement.setInt(6, offer.costPoints());
                    statement.setInt(7, offer.pointsType());
                    statement.setInt(8, offer.amount());
                    statement.setInt(9, offer.limitedStack());
                    statement.setInt(10, 0);
                    statement.setInt(11, offer.orderNumber());
                    statement.setInt(12, offer.offerIdClient());
                    statement.setInt(13, offer.songId());
                    statement.setString(14, offer.extradata());
                    setLegacyEnumBoolean(statement, 15, offer.haveOffer());
                    setLegacyEnumBoolean(statement, 16, offer.clubOnly());
                    statement.addBatch();
                }
            } catch (SQLException exception) {
                throw exception;
            }
            statement.executeBatch();
        }
    }

    private static void upsertBuilderPages(Connection connection, CatalogVersionSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_BUILDER_PAGE_SQL)) {
            ArrayList<CatalogPageSnapshot> pages = new ArrayList<>(snapshot.pages());
            pages.sort(Comparator.comparingInt(page -> depth(snapshot, page)));
            for (CatalogPageSnapshot page : pages) {
                if (page.catalogType() != CatalogPageType.BUILDER) continue;
                statement.setInt(1, page.pageId());
                statement.setInt(2, page.parentId());
                statement.setString(3, page.caption());
                statement.setString(4, page.pageLayout());
                statement.setInt(5, page.iconColor());
                statement.setInt(6, page.iconImage());
                statement.setInt(7, page.orderNum());
                setLegacyEnumBoolean(statement, 8, page.visible());
                setLegacyEnumBoolean(statement, 9, page.enabled());
                statement.setString(10, page.pageHeadline());
                statement.setString(11, page.pageTeaser());
                statement.setString(12, page.pageSpecial());
                statement.setString(13, page.pageText1());
                statement.setString(14, page.pageText2());
                statement.setString(15, page.pageTextDetails());
                statement.setString(16, page.pageTextTeaser());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void upsertBuilderOffers(Connection connection, CatalogVersionSnapshot snapshot)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_BUILDER_OFFER_SQL)) {
            for (CatalogOfferSnapshot offer : snapshot.offers()) {
                if (offer.catalogType() != CatalogPageType.BUILDER) continue;
                if (snapshot.page(offer.catalogType(), offer.pageId()).isEmpty()) continue;
                statement.setInt(1, offer.offerId());
                statement.setString(2, offer.itemIds());
                statement.setInt(3, offer.pageId());
                statement.setString(4, offer.catalogName());
                statement.setInt(5, offer.orderNumber());
                statement.setString(6, offer.extradata());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteAbsent(Connection connection, String sql, long versionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, versionId);
            statement.executeUpdate();
        }
    }

    private static void setLegacyEnumBoolean(PreparedStatement statement, int index, boolean value)
            throws SQLException {
        statement.setString(index, value ? "1" : "0");
    }

    private static int depth(CatalogVersionSnapshot snapshot, CatalogPageSnapshot page) {
        int depth = 0;
        int parentId = page.parentId();
        Set<Integer> visited = new HashSet<>();
        while (parentId > 0 && visited.add(parentId)) {
            CatalogPageSnapshot parent =
                    snapshot.page(page.catalogType(), parentId).orElse(null);
            if (parent == null) break;
            depth++;
            parentId = parent.parentId();
        }
        return depth;
    }
}
