package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Writes one catalog entity without rebuilding unrelated live catalog rows. */
public final class JdbcCatalogLiveEntityWriter implements CatalogLiveEntityWriter {
    static final String UPSERT_NORMAL_PAGE_SQL = "INSERT INTO catalog_pages "
            + "(id,parent_id,caption_save,caption,page_layout,icon_color,icon_image,min_rank,order_num,visible,enabled,"
            + "club_only,catalog_mode,vip_only,page_headline,page_teaser,page_special,page_text1,page_text2,"
            + "page_text_details,page_text_teaser,room_id,includes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),caption_save=VALUES(caption_save),"
            + "caption=VALUES(caption),page_layout=VALUES(page_layout),icon_color=VALUES(icon_color),"
            + "icon_image=VALUES(icon_image),min_rank=VALUES(min_rank),order_num=VALUES(order_num),"
            + "visible=VALUES(visible),enabled=VALUES(enabled),club_only=VALUES(club_only),"
            + "catalog_mode=VALUES(catalog_mode),vip_only=VALUES(vip_only),page_headline=VALUES(page_headline),"
            + "page_teaser=VALUES(page_teaser),page_special=VALUES(page_special),page_text1=VALUES(page_text1),"
            + "page_text2=VALUES(page_text2),page_text_details=VALUES(page_text_details),"
            + "page_text_teaser=VALUES(page_text_teaser),room_id=VALUES(room_id),includes=VALUES(includes)";
    static final String UPSERT_BUILDER_PAGE_SQL = "INSERT INTO catalog_pages_bc "
            + "(id,parent_id,caption,page_layout,icon_color,icon_image,order_num,visible,enabled,page_headline,"
            + "page_teaser,page_special,page_text1,page_text2,page_text_details,page_text_teaser) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),"
            + "caption=VALUES(caption),page_layout=VALUES(page_layout),icon_color=VALUES(icon_color),"
            + "icon_image=VALUES(icon_image),order_num=VALUES(order_num),visible=VALUES(visible),"
            + "enabled=VALUES(enabled),page_headline=VALUES(page_headline),page_teaser=VALUES(page_teaser),"
            + "page_special=VALUES(page_special),page_text1=VALUES(page_text1),page_text2=VALUES(page_text2),"
            + "page_text_details=VALUES(page_text_details),page_text_teaser=VALUES(page_text_teaser)";

    private final Gson gson;

    public JdbcCatalogLiveEntityWriter(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public void apply(Connection connection, CatalogChangeEntry change) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(change, "change");
        if (change.entityType() != CatalogEntityType.PAGE) {
            throw new UnsupportedOperationException("Live offer mutations are not connected yet");
        }
        if (change.afterJson() == null) {
            deletePage(connection, change);
            return;
        }
        CatalogPageSnapshot page = gson.fromJson(change.afterJson(), CatalogPageSnapshot.class);
        if (page.pageId() != change.entityId() || page.catalogType() != change.catalogType()) {
            throw new IllegalArgumentException("Page JSON identity does not match the live change");
        }
        if (page.catalogType() == CatalogPageType.BUILDER) upsertBuilderPage(connection, page);
        else upsertNormalPage(connection, page);
    }

    private static void deletePage(Connection connection, CatalogChangeEntry change) throws SQLException {
        String table = change.catalogType() == CatalogPageType.BUILDER ? "catalog_pages_bc" : "catalog_pages";
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            statement.setInt(1, change.entityId());
            if (statement.executeUpdate() != 1) throw new SQLException("Live catalog page disappeared before deletion");
        }
    }

    private static void upsertNormalPage(Connection connection, CatalogPageSnapshot page) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_NORMAL_PAGE_SQL)) {
            statement.setInt(1, page.pageId());
            statement.setInt(2, page.parentId());
            statement.setString(3, page.captionSave());
            statement.setString(4, page.caption());
            statement.setString(5, page.pageLayout());
            statement.setInt(6, page.iconColor());
            statement.setInt(7, page.iconImage());
            statement.setInt(8, page.minRank());
            statement.setInt(9, page.orderNum());
            statement.setBoolean(10, page.visible());
            statement.setBoolean(11, page.enabled());
            statement.setBoolean(12, page.clubOnly());
            statement.setString(13, page.catalogMode());
            statement.setBoolean(14, page.vipOnly());
            statement.setString(15, page.pageHeadline());
            statement.setString(16, page.pageTeaser());
            statement.setString(17, page.pageSpecial());
            statement.setString(18, page.pageText1());
            statement.setString(19, page.pageText2());
            statement.setString(20, page.pageTextDetails());
            statement.setString(21, page.pageTextTeaser());
            statement.setInt(22, page.roomId());
            statement.setString(23, page.includes());
            if (statement.executeUpdate() == 0) throw new SQLException("Live catalog page upsert changed no row");
        }
    }

    private static void upsertBuilderPage(Connection connection, CatalogPageSnapshot page) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_BUILDER_PAGE_SQL)) {
            statement.setInt(1, page.pageId());
            statement.setInt(2, page.parentId());
            statement.setString(3, page.caption());
            statement.setString(4, page.pageLayout());
            statement.setInt(5, page.iconColor());
            statement.setInt(6, page.iconImage());
            statement.setInt(7, page.orderNum());
            statement.setBoolean(8, page.visible());
            statement.setBoolean(9, page.enabled());
            statement.setString(10, page.pageHeadline());
            statement.setString(11, page.pageTeaser());
            statement.setString(12, page.pageSpecial());
            statement.setString(13, page.pageText1());
            statement.setString(14, page.pageText2());
            statement.setString(15, page.pageTextDetails());
            statement.setString(16, page.pageTextTeaser());
            if (statement.executeUpdate() == 0) throw new SQLException("Live builder page upsert changed no row");
        }
    }
}
