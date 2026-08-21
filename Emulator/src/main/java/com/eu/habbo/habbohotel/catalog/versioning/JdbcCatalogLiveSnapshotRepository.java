package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcCatalogLiveSnapshotRepository implements CatalogLiveSnapshotRepository {
    static final String LOAD_NORMAL_PAGES_SQL = "SELECT id AS page_id, parent_id, caption_save, caption, page_layout, "
            + "icon_color, icon_image, min_rank, order_num, visible, enabled, club_only, catalog_mode, vip_only, "
            + "page_headline, page_teaser, COALESCE(page_special, '') AS page_special, page_text1, page_text2, "
            + "page_text_details, page_text_teaser, COALESCE(room_id, 0) AS room_id, includes "
            + "FROM catalog_pages ORDER BY id FOR UPDATE";
    static final String LOAD_NORMAL_OFFERS_SQL = "SELECT id AS offer_id, item_ids, page_id, catalog_name, "
            + "cost_credits, cost_points, points_type, amount, limited_stack, order_number, offer_id AS offer_id_client, "
            + "song_id, extradata, have_offer, club_only FROM catalog_items ORDER BY id FOR UPDATE";
    static final String LOAD_BUILDER_PAGES_SQL = "SELECT id AS page_id, parent_id, '' AS caption_save, caption, "
            + "page_layout, icon_color, icon_image, 1 AS min_rank, order_num, visible, enabled, 0 AS club_only, "
            + "'BUILDER' AS catalog_mode, 0 AS vip_only, page_headline, page_teaser, "
            + "COALESCE(page_special, '') AS page_special, page_text1, page_text2, page_text_details, "
            + "page_text_teaser, 0 AS room_id, '' AS includes FROM catalog_pages_bc ORDER BY id FOR UPDATE";
    static final String LOAD_BUILDER_OFFERS_SQL = "SELECT id AS offer_id, item_ids, page_id, catalog_name, "
            + "0 AS cost_credits, 0 AS cost_points, 0 AS points_type, 1 AS amount, 0 AS limited_stack, order_number, "
            + "-1 AS offer_id_client, 0 AS song_id, extradata, 1 AS have_offer, 0 AS club_only "
            + "FROM catalog_items_bc ORDER BY id FOR UPDATE";

    @Override
    public CatalogVersionSnapshot load(Connection connection, CatalogVersion version) throws SQLException {
        List<CatalogPageSnapshot> normalPages = loadPages(connection, LOAD_NORMAL_PAGES_SQL, CatalogPageType.NORMAL);
        List<CatalogOfferSnapshot> normalOffers =
                loadOffers(connection, LOAD_NORMAL_OFFERS_SQL, CatalogPageType.NORMAL);
        List<CatalogPageSnapshot> builderPages = loadPages(connection, LOAD_BUILDER_PAGES_SQL, CatalogPageType.BUILDER);
        List<CatalogOfferSnapshot> builderOffers =
                loadOffers(connection, LOAD_BUILDER_OFFERS_SQL, CatalogPageType.BUILDER);
        List<CatalogPageSnapshot> pages = new ArrayList<>(normalPages);
        pages.addAll(builderPages);
        List<CatalogOfferSnapshot> offers = new ArrayList<>(normalOffers);
        offers.addAll(builderOffers);
        return new CatalogVersionSnapshot(version, pages, offers);
    }

    private static List<CatalogPageSnapshot> loadPages(Connection connection, String sql, CatalogPageType catalogType)
            throws SQLException {
        List<CatalogPageSnapshot> pages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                pages.add(new CatalogPageSnapshot(
                        catalogType,
                        resultSet.getInt("page_id"),
                        resultSet.getInt("parent_id"),
                        resultSet.getString("caption_save"),
                        resultSet.getString("caption"),
                        resultSet.getString("page_layout"),
                        resultSet.getInt("icon_color"),
                        resultSet.getInt("icon_image"),
                        resultSet.getInt("min_rank"),
                        resultSet.getInt("order_num"),
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "visible"),
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "enabled"),
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "club_only"),
                        resultSet.getString("catalog_mode"),
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "vip_only"),
                        resultSet.getString("page_headline"),
                        resultSet.getString("page_teaser"),
                        resultSet.getString("page_special"),
                        resultSet.getString("page_text1"),
                        resultSet.getString("page_text2"),
                        resultSet.getString("page_text_details"),
                        resultSet.getString("page_text_teaser"),
                        resultSet.getInt("room_id"),
                        resultSet.getString("includes")));
            }
        }
        return pages;
    }

    private static List<CatalogOfferSnapshot> loadOffers(Connection connection, String sql, CatalogPageType catalogType)
            throws SQLException {
        List<CatalogOfferSnapshot> offers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                offers.add(new CatalogOfferSnapshot(
                        catalogType,
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
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "have_offer"),
                        JdbcCatalogVersionRepository.readStrictBoolean(resultSet, "club_only")));
            }
        }
        return offers;
    }
}
