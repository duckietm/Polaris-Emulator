package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Comparator;
import java.util.Objects;

public final class CatalogSqlExportService {
    public String export(CatalogVersionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        StringBuilder sql = new StringBuilder("-- Catalog Studio restricted SQL export.\n"
                + "DELETE FROM catalog_items;\n"
                + "DELETE FROM catalog_pages;\n");
        snapshot.pages().stream()
                .sorted(Comparator.comparing(CatalogPageSnapshot::catalogType)
                        .thenComparingInt(CatalogPageSnapshot::pageId))
                .forEach(page -> appendPage(sql, page));
        snapshot.offers().stream()
                .sorted(Comparator.comparing(CatalogOfferSnapshot::catalogType)
                        .thenComparingInt(CatalogOfferSnapshot::offerId))
                .forEach(offer -> appendOffer(sql, offer));
        return sql.toString();
    }

    private static void appendPage(StringBuilder sql, CatalogPageSnapshot page) {
        sql.append("INSERT INTO catalog_pages (id, catalog_type, parent_id, caption_save, caption, page_layout, "
                        + "icon_color, icon_image, min_rank, order_num, visible, enabled, club_only, catalog_mode, "
                        + "vip_only, page_headline, page_teaser, page_special, page_text1, page_text2, "
                        + "page_text_details, page_text_teaser, room_id, includes) VALUES (")
                .append(page.pageId())
                .append(", ")
                .append(q(page.catalogType().name()))
                .append(", ")
                .append(page.parentId())
                .append(", ")
                .append(q(page.captionSave()))
                .append(", ")
                .append(q(page.caption()))
                .append(", ")
                .append(q(page.pageLayout()))
                .append(", ")
                .append(page.iconColor())
                .append(", ")
                .append(page.iconImage())
                .append(", ")
                .append(page.minRank())
                .append(", ")
                .append(page.orderNum())
                .append(", ")
                .append(bit(page.visible()))
                .append(", ")
                .append(bit(page.enabled()))
                .append(", ")
                .append(bit(page.clubOnly()))
                .append(", ")
                .append(q(page.catalogMode()))
                .append(", ")
                .append(bit(page.vipOnly()))
                .append(", ")
                .append(q(page.pageHeadline()))
                .append(", ")
                .append(q(page.pageTeaser()))
                .append(", ")
                .append(q(page.pageSpecial()))
                .append(", ")
                .append(q(page.pageText1()))
                .append(", ")
                .append(q(page.pageText2()))
                .append(", ")
                .append(q(page.pageTextDetails()))
                .append(", ")
                .append(q(page.pageTextTeaser()))
                .append(", ")
                .append(page.roomId())
                .append(", ")
                .append(q(page.includes()))
                .append(");\n");
    }

    private static void appendOffer(StringBuilder sql, CatalogOfferSnapshot offer) {
        sql.append("INSERT INTO catalog_items (id, catalog_type, item_ids, page_id, catalog_name, cost_credits, "
                        + "cost_points, points_type, amount, limited_stack, order_number, offer_id, song_id, extradata, "
                        + "have_offer, club_only) VALUES (")
                .append(offer.offerId())
                .append(", ")
                .append(q(offer.catalogType().name()))
                .append(", ")
                .append(q(offer.itemIds()))
                .append(", ")
                .append(offer.pageId())
                .append(", ")
                .append(q(offer.catalogName()))
                .append(", ")
                .append(offer.costCredits())
                .append(", ")
                .append(offer.costPoints())
                .append(", ")
                .append(offer.pointsType())
                .append(", ")
                .append(offer.amount())
                .append(", ")
                .append(offer.limitedStack())
                .append(", ")
                .append(offer.orderNumber())
                .append(", ")
                .append(offer.offerIdClient())
                .append(", ")
                .append(offer.songId())
                .append(", ")
                .append(q(offer.extradata()))
                .append(", ")
                .append(bit(offer.haveOffer()))
                .append(", ")
                .append(bit(offer.clubOnly()))
                .append(");\n");
    }

    private static int bit(boolean value) {
        return value ? 1 : 0;
    }

    private static String q(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }
}
