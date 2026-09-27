package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.eu.habbo.util.SqlLikeEscaper;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MarketplaceOffersQueryTest {

    private static final int NOW = 1_000_000;
    private static final int MIN_LISTING_PRICE = 1;
    private static final int MAX_LISTING_PRICE = 1_000_000_000;

    private static MarketplaceOffersQuery.Request request(
            int minPrice, int maxPrice, String search, int sort, boolean combineUniques) {
        return new MarketplaceOffersQuery.Request(minPrice, maxPrice, search, sort, combineUniques);
    }

    private static MarketplaceOffersQuery.Request plain(int sort, boolean combineUniques) {
        return request(-1, -1, "", sort, combineUniques);
    }

    private static long placeholders(String sql) {
        return sql.chars().filter(character -> character == '?').count();
    }

    @Test
    void aggregatesTheListingsOnceAndTakesTheCheapestFromTheSamePass() {
        String sql = MarketplaceOffersQuery.sql(plain(1, false));

        assertTrue(sql.contains("GROUP_CONCAT(l.id ORDER BY l.price ASC, l.id ASC SEPARATOR ',')"), sql);
        assertTrue(sql.contains("INNER JOIN marketplace_items m ON m.id = g.cheapest_id"), sql);
        assertFalse(sql.contains("MIN(e.price)"), "no correlated minimum-price subquery");
        assertFalse(sql.contains("marketplace_items a"), "no self-join around the result");
        assertTrue(sql.contains("FLOOR(AVG(l.price)) AS avg"), "the average is whole credits");
        assertTrue(sql.endsWith("LIMIT 250"), sql);
    }

    @Test
    void countsTheSalesOfTheDayByRange() {
        String sql = MarketplaceOffersQuery.sql(plain(1, false));

        assertFalse(sql.contains("from_unixtime"), "the sale time is compared as stored, so an index can serve it");
        assertTrue(sql.contains("sl.sold_timestamp >= UNIX_TIMESTAMP(CURDATE())"), sql);
        assertTrue(sql.contains("sl.sold_timestamp < UNIX_TIMESTAMP(CURDATE() + INTERVAL 1 DAY)"), sql);
        assertTrue(sql.contains("COALESCE(s.sold_today, 0) AS sold_count_today"), sql);
    }

    @Test
    void keepsOneRowPerSerialUnlessSerialsAreCombined() {
        assertTrue(MarketplaceOffersQuery.sql(plain(1, false)).contains("GROUP BY li.item_id, li.limited_data\n"));

        String combined = MarketplaceOffersQuery.sql(plain(1, true));
        assertTrue(combined.contains("GROUP BY li.item_id\n"), combined);
        assertFalse(combined.contains("li.limited_data"), combined);
    }

    @Test
    void mapsEverySortCodeToItsOrderWithADeterministicTieBreak() {
        assertOrder(6, "g.number ASC");
        assertOrder(5, "g.number DESC");
        assertOrder(4, "sold_count_today ASC");
        assertOrder(3, "sold_count_today DESC");
        assertOrder(2, "g.minPrice ASC");
        assertOrder(1, "g.minPrice DESC");
        assertOrder(0, "g.minPrice DESC");
    }

    private static void assertOrder(int sort, String clause) {
        String sql = MarketplaceOffersQuery.sql(plain(sort, false));
        assertTrue(
                sql.endsWith("ORDER BY " + clause + ", g.base_item_id ASC, m.id ASC\nLIMIT 250"),
                () -> "sort " + sort + ":\n" + sql);
    }

    @Test
    void appliesThePriceBandOnlyWhenItIsRequested() {
        String lower = "AND CEIL(l.price + (l.price / 100)) >= ?";
        String upper = "AND CEIL(l.price + (l.price / 100)) <= ?";

        String none = MarketplaceOffersQuery.sql(request(0, 0, "", 1, false));
        assertFalse(none.contains("CEIL("), none);

        String both = MarketplaceOffersQuery.sql(request(40, 60, "", 1, false));
        assertTrue(both.contains(lower) && both.contains(upper), both);

        String upperBelowLower = MarketplaceOffersQuery.sql(request(40, 30, "", 1, false));
        assertTrue(upperBelowLower.contains(lower), upperBelowLower);
        assertFalse(upperBelowLower.contains(upper), upperBelowLower);
    }

    @Test
    void listsOnlyFurniTheCatalogSellsAndCountsEachListingOnce() {
        String sql = MarketplaceOffersQuery.sql(plain(1, false));

        assertTrue(sql.contains("SELECT (ci.item_ids + 0) AS base_item_id"), sql);
        assertTrue(sql.contains("GROUP BY (ci.item_ids + 0)\n) cat ON cat.base_item_id = g.base_item_id"), sql);
        assertFalse(sql.contains("LIKE"), sql);
    }

    @Test
    void searchesTheFurniNameAndTheCatalogNamesThroughBoundValuesOnly() {
        String sql = MarketplaceOffersQuery.sql(request(-1, -1, "sofa", 1, false));

        assertTrue(sql.contains("MAX(ci.catalog_name LIKE ?) AS name_match"), sql);
        assertTrue(sql.contains("WHERE (bi.public_name LIKE ? OR cat.name_match = 1)"), sql);
        assertFalse(sql.contains("sofa"), "the search text must never reach the statement text");
    }

    @Test
    void treatsAMissingSearchAsNoSearch() {
        MarketplaceOffersQuery.Request request = request(-1, -1, null, 1, false);

        assertFalse(request.hasSearch());
        assertFalse(MarketplaceOffersQuery.sql(request).contains("LIKE"));
    }

    @Test
    void bindsOneValuePerPlaceholder() throws Exception {
        assertBindCount(plain(1, false), 3);
        assertBindCount(request(40, 60, "", 1, false), 5);
        assertBindCount(request(40, 30, "", 1, false), 4);
        assertBindCount(request(40, 60, "sofa", 1, false), 7);
        assertBindCount(request(-1, -1, "sofa", 1, true), 5);
    }

    private static void assertBindCount(MarketplaceOffersQuery.Request request, int expected) throws Exception {
        String sql = MarketplaceOffersQuery.sql(request);
        assertEquals(expected, placeholders(sql), sql);

        PreparedStatement statement = mock(PreparedStatement.class);
        assertEquals(
                expected + 1,
                MarketplaceOffersQuery.bind(statement, request, NOW, MIN_LISTING_PRICE, MAX_LISTING_PRICE));
    }

    @Test
    void bindsTheWindowTheListingBoundsTheBandAndTheEscapedSearchInOrder() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        MarketplaceOffersQuery.bind(
                statement, request(40, 60, "50%_off", 2, false), NOW, MIN_LISTING_PRICE, MAX_LISTING_PRICE);

        String pattern = "%" + SqlLikeEscaper.escape("50%_off") + "%";
        assertEquals("%50\\%\\_off%", pattern);

        InOrder order = inOrder(statement);
        order.verify(statement).setInt(1, NOW - MarketplaceOffersQuery.LISTING_WINDOW_SECONDS);
        order.verify(statement).setInt(2, MIN_LISTING_PRICE);
        order.verify(statement).setInt(3, MAX_LISTING_PRICE);
        order.verify(statement).setInt(4, 40);
        order.verify(statement).setInt(5, 60);
        order.verify(statement).setString(6, pattern);
        order.verify(statement).setString(7, pattern);
        verifyNoMoreInteractions(statement);
    }
}
