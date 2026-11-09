package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import polaris.util.SqlLikeEscaper;

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

    // The offer kind joined the grouping with the Collectibles marketplace: a collectible offer and an older credit
    // offer of the same nft_* furni are two rows, priced in two currencies.
    @Test
    void keepsOneRowPerSerialUnlessSerialsAreCombined() {
        assertTrue(
                MarketplaceOffersQuery.sql(plain(1, false)).contains("GROUP BY li.item_id, l.nft, li.limited_data\n"));

        String combined = MarketplaceOffersQuery.sql(plain(1, true));
        assertTrue(combined.contains("GROUP BY li.item_id, l.nft\n"), combined);
        assertFalse(combined.contains("li.limited_data"), combined);
    }

    @Test
    void listsOnlyFurniWhileTheCollectiblesMarketplaceIsOff() {
        String sql = MarketplaceOffersQuery.sql(plain(1, false));

        assertTrue(sql.contains("AND l.nft = 0\n"), sql);
    }

    @Test
    void filtersTheOfferSourceOfAIRsTypeMenu() {
        assertTrue(collectibles(MarketplaceOffersQuery.SOURCE_ALL)
                .contains("WHERE (cat.base_item_id IS NOT NULL OR g.nft = 1)"));
        assertFalse(collectibles(MarketplaceOffersQuery.SOURCE_ALL).contains("AND l.nft ="));
        assertTrue(collectibles(MarketplaceOffersQuery.SOURCE_COLLECTIBLES).contains("AND l.nft = 1\n"));
        assertTrue(collectibles(MarketplaceOffersQuery.SOURCE_FURNI).contains("AND l.nft = 0\n"));

        String noneLeft = MarketplaceOffersQuery.sql(new MarketplaceOffersQuery.Request(
                -1, -1, "", 1, false, MarketplaceOffersQuery.SOURCE_COLLECTIBLES, false, 600));
        assertTrue(noneLeft.contains("AND 0 = 1\n"), noneLeft);
    }

    private static String collectibles(int source) {
        return MarketplaceOffersQuery.sql(new MarketplaceOffersQuery.Request(-1, -1, "", 1, false, source, true, 600));
    }

    @Test
    void bindsTheCollectiblesListingWindowAfterTheFurniOne() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        MarketplaceOffersQuery.bind(
                statement,
                new MarketplaceOffersQuery.Request(-1, -1, "", 1, false, MarketplaceOffersQuery.SOURCE_ALL, true, 600),
                NOW,
                MIN_LISTING_PRICE,
                MAX_LISTING_PRICE);

        InOrder order = inOrder(statement);
        order.verify(statement).setInt(1, NOW - MarketplaceOffersQuery.LISTING_WINDOW_SECONDS);
        order.verify(statement).setInt(2, NOW - 600);
        order.verify(statement).setInt(3, MIN_LISTING_PRICE);
        order.verify(statement).setInt(4, MAX_LISTING_PRICE);
        verifyNoMoreInteractions(statement);
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
        // The buyer's price: commission on top for furni, the asking price for a collectible.
        String price = "(CASE WHEN l.nft = 1 THEN l.price ELSE CEIL(l.price + (l.price / 100)) END)";
        String lower = "AND " + price + " >= ?";
        String upper = "AND " + price + " <= ?";

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
        assertTrue(sql.contains("    AND (bi.public_name LIKE ? OR cat.name_match = 1)"), sql);
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
        assertBindCount(plain(1, false), 4);
        assertBindCount(request(40, 60, "", 1, false), 6);
        assertBindCount(request(40, 30, "", 1, false), 5);
        assertBindCount(request(40, 60, "sofa", 1, false), 8);
        assertBindCount(request(-1, -1, "sofa", 1, true), 6);
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
        order.verify(statement).setInt(2, NOW - MarketplaceOffersQuery.LISTING_WINDOW_SECONDS);
        order.verify(statement).setInt(3, MIN_LISTING_PRICE);
        order.verify(statement).setInt(4, MAX_LISTING_PRICE);
        order.verify(statement).setInt(5, 40);
        order.verify(statement).setInt(6, 60);
        order.verify(statement).setString(7, pattern);
        order.verify(statement).setString(8, pattern);
        verifyNoMoreInteractions(statement);
    }
}
