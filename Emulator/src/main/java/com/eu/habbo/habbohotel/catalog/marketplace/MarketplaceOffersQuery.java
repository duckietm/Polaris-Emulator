package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.util.SqlLikeEscaper;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The public marketplace listing: one row per furni, or per limited-edition serial unless the
 * serials are combined, represented by its cheapest active listing and carrying the count, the
 * average and the price range of every active listing of that furni.
 *
 * <p>The listings are aggregated in one pass that also names the cheapest one, the catalog link
 * and the sales of the day are each computed once and joined, and the requested order is
 * applied to the final rows before the result cap. Only furni the catalog sells are listed.
 * Prices stay without the sale commission: the offers composer adds it.
 */
final class MarketplaceOffersQuery {

    static final int RESULT_LIMIT = 250;

    /** Listings older than this are expired and no longer offered. */
    static final int LISTING_WINDOW_SECONDS = 172800;

    private MarketplaceOffersQuery() {}

    /**
     * One search. A price bound is applied when it is positive (the upper one only above the
     * lower one) and compared with the commission-inclusive price the buyer pays.
     */
    record Request(int minPrice, int maxPrice, String search, int sort, boolean combineUniques) {

        boolean hasMinPrice() {
            return this.minPrice > 0;
        }

        boolean hasMaxPrice() {
            return this.maxPrice > 0 && this.maxPrice > this.minPrice;
        }

        boolean hasSearch() {
            return this.search != null && !this.search.isEmpty();
        }
    }

    static String sql(Request request) {
        StringBuilder sql = new StringBuilder(1600);

        sql.append("SELECT m.id, m.item_id, m.user_id, m.price, m.timestamp, m.sold_timestamp, m.state,")
                .append(" g.base_item_id, mi.limited_data AS ltd_data,")
                .append(" g.avg, g.minPrice, g.maxPrice, g.number,")
                .append(" COALESCE(s.sold_today, 0) AS sold_count_today\n");

        // Every active listing of a furni, in the listing window and the price band.
        sql.append("FROM (\n")
                .append("    SELECT li.item_id AS base_item_id, AVG(l.price) AS avg, MIN(l.price) AS minPrice,")
                .append(" MAX(l.price) AS maxPrice, COUNT(*) AS number,\n")
                .append("        CAST(SUBSTRING_INDEX(GROUP_CONCAT(l.id ORDER BY l.price ASC, l.id ASC SEPARATOR ','),")
                .append(" ',', 1) AS UNSIGNED) AS cheapest_id\n")
                .append("    FROM marketplace_items l\n")
                .append("    INNER JOIN items li ON li.id = l.item_id\n")
                .append("    WHERE l.state = 1 AND l.timestamp > ? AND l.price BETWEEN ? AND ?\n");
        if (request.hasMinPrice()) {
            sql.append("        AND CEIL(l.price + (l.price / 100)) >= ?\n");
        }
        if (request.hasMaxPrice()) {
            sql.append("        AND CEIL(l.price + (l.price / 100)) <= ?\n");
        }
        sql.append(
                        request.combineUniques()
                                ? "    GROUP BY li.item_id\n"
                                : "    GROUP BY li.item_id, li.limited_data\n")
                .append(") g\n")
                .append("INNER JOIN items_base bi ON bi.id = g.base_item_id\n");

        // The furni the catalog sells. item_ids is text: its numeric value is the furni it
        // links, which is how a listing has always been matched to the catalog. Grouping keeps
        // a furni sold on several catalog rows from multiplying its listings.
        sql.append("INNER JOIN (\n").append("    SELECT (ci.item_ids + 0) AS base_item_id");
        if (request.hasSearch()) {
            sql.append(", MAX(ci.catalog_name LIKE ?) AS name_match");
        }
        sql.append("\n")
                .append("    FROM catalog_items ci\n")
                .append("    GROUP BY base_item_id\n")
                .append(") cat ON cat.base_item_id = g.base_item_id\n");

        // The cheapest listing represents the furni.
        sql.append("INNER JOIN marketplace_items m ON m.id = g.cheapest_id\n")
                .append("INNER JOIN items mi ON mi.id = m.item_id\n");

        // Sales of the day, per furni, whatever the serial.
        sql.append("LEFT JOIN (\n")
                .append("    SELECT si.item_id AS base_item_id, COUNT(*) AS sold_today\n")
                .append("    FROM marketplace_items sl\n")
                .append("    INNER JOIN items si ON si.id = sl.item_id\n")
                .append("    WHERE sl.state = 2 AND sl.sold_timestamp >= UNIX_TIMESTAMP(CURDATE())")
                .append(" AND sl.sold_timestamp < UNIX_TIMESTAMP(CURDATE() + INTERVAL 1 DAY)\n")
                .append("    GROUP BY si.item_id\n")
                .append(") s ON s.base_item_id = g.base_item_id\n");

        if (request.hasSearch()) {
            sql.append("WHERE (bi.public_name LIKE ? OR cat.name_match = 1)\n");
        }

        return sql.append("ORDER BY ")
                .append(orderBy(request.sort()))
                .append(", g.base_item_id ASC, m.id ASC\n")
                .append("LIMIT ")
                .append(RESULT_LIMIT)
                .toString();
    }

    /**
     * Binds the values in statement order: the listing window, the listing price bounds, the
     * requested band, then the escaped search once for the catalog names and once for the furni
     * name. Returns the next free parameter index.
     */
    static int bind(PreparedStatement statement, Request request, int nowUnix, int minListingPrice, int maxListingPrice)
            throws SQLException {
        int index = 1;
        statement.setInt(index++, nowUnix - LISTING_WINDOW_SECONDS);
        statement.setInt(index++, minListingPrice);
        statement.setInt(index++, maxListingPrice);
        if (request.hasMinPrice()) {
            statement.setInt(index++, request.minPrice());
        }
        if (request.hasMaxPrice()) {
            statement.setInt(index++, request.maxPrice());
        }
        if (request.hasSearch()) {
            String pattern = "%" + SqlLikeEscaper.escape(request.search()) + "%";
            statement.setString(index++, pattern);
            statement.setString(index++, pattern);
        }
        return index;
    }

    private static String orderBy(int sort) {
        return switch (sort) {
            case 6 -> "g.number ASC";
            case 5 -> "g.number DESC";
            case 4 -> "sold_count_today ASC";
            case 3 -> "sold_count_today DESC";
            case 2 -> "g.minPrice ASC";
            default -> "g.minPrice DESC";
        };
    }
}
