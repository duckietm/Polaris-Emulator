package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Runs the marketplace offers query on a fully migrated MariaDB against a fixture of listings,
 * limited-edition serials, catalog rows and sales, and checks what a player's search returns.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketplaceOffersQueryIT {

    private static final int SOFA = 1_900_000_001;
    private static final int THRONE = 1_900_000_002;
    private static final int LAMP = 1_900_000_003;
    private static final Set<Integer> FIXTURE_FURNI = Set.of(SOFA, THRONE, LAMP);

    private static final int OPEN = 1;
    private static final int SOLD = 2;

    private static final Path INDEX_MIGRATION =
            Path.of("src/main/resources/db/migration/V20260923200000__marketplace_query_indexes.sql");

    private HikariDataSource dataSource;
    private int now;
    private int startOfDay;

    private record Offer(int id, int furni, String ltdData, int minPrice, int number, int average, int soldToday) {}

    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable error) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI", error);
            }
            assumeTrue(false, "Docker/Testcontainers is unavailable: " + error.getMessage());
        }
    }

    @BeforeAll
    void migrateAndLoadTheFixture() throws Exception {
        requireDocker();
        this.dataSource = TestDatabase.freshDatabase("marketplace_offers");
        MigrationRunner.migrate(this.dataSource);
        try (Connection connection = this.dataSource.getConnection()) {
            loadFixture(connection);
        }
    }

    @AfterAll
    void closeTheDatabase() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    @Test
    void representsEachFurniByItsCheapestListingAndCountsEveryActiveListingOnce() throws Exception {
        List<Offer> offers = offers(-1, -1, "", 2, false);

        assertEquals(List.of(listingId(2), listingId(12), listingId(11)), ids(offers), "cheapest first, lamp not sold");

        Offer sofa = offers.get(0);
        assertEquals(SOFA, sofa.furni());
        assertEquals("0:0", sofa.ltdData());
        assertEquals(30, sofa.minPrice(), "the expired listing at 5 is not offered");
        assertEquals(3, sofa.number(), "three active listings, not multiplied by the two catalog rows");
        assertEquals(36, sofa.average(), "110 / 3 in whole credits");
        assertEquals(1, sofa.soldToday(), "yesterday's sale is not counted");

        Offer secondSerial = offers.get(1);
        assertEquals(THRONE, secondSerial.furni());
        assertEquals("2:5", secondSerial.ltdData());
        assertEquals(400, secondSerial.minPrice());
        assertEquals(1, secondSerial.number());
        assertEquals(0, secondSerial.soldToday());

        Offer firstSerial = offers.get(2);
        assertEquals("1:5", firstSerial.ltdData());
        assertEquals(500, firstSerial.minPrice());
    }

    @Test
    void combinesTheSerialsOfALimitedEditionWhenAsked() throws Exception {
        List<Offer> offers = offers(-1, -1, "", 2, true);

        assertEquals(List.of(listingId(2), listingId(12)), ids(offers));
        Offer throne = offers.get(1);
        assertEquals("2:5", throne.ltdData(), "the cheapest serial represents the furni");
        assertEquals(400, throne.minPrice());
        assertEquals(2, throne.number());
        assertEquals(450, throne.average());
    }

    @Test
    void thePriceBandSelectsListingsNotFurni() throws Exception {
        // 50 costs 51 with the commission and is the only listing between 40 and 60.
        List<Offer> offers = offers(40, 60, "", 2, false);

        assertEquals(List.of(listingId(1)), ids(offers));
        assertEquals(50, offers.get(0).minPrice());
        assertEquals(1, offers.get(0).number());
    }

    @Test
    void searchesTheFurniNameAndTheCatalogNamesLiterally() throws Exception {
        assertEquals(List.of(listingId(2)), ids(offers(-1, -1, "Test Sofa", 2, false)), "furni name only");
        assertEquals(
                List.of(listingId(12), listingId(11)), ids(offers(-1, -1, "throne_page", 2, false)), "catalog name");
        assertEquals(List.of(), ids(offers(-1, -1, "lamp", 2, false)), "furni the catalog does not sell");
        assertEquals(List.of(), ids(offers(-1, -1, "%", 2, false)), "a wildcard is matched literally");
    }

    @Test
    void ordersTheFinalRowsAsRequested() throws Exception {
        assertEquals(
                List.of(listingId(11), listingId(12), listingId(2)), ids(offers(-1, -1, "", 1, false)), "price desc");
        assertEquals(
                List.of(listingId(2), listingId(11), listingId(12)), ids(offers(-1, -1, "", 5, false)), "count desc");
        assertEquals(
                List.of(listingId(11), listingId(12), listingId(2)), ids(offers(-1, -1, "", 6, false)), "count asc");
        assertEquals(
                List.of(listingId(2), listingId(11), listingId(12)), ids(offers(-1, -1, "", 3, false)), "sold desc");
        assertEquals(
                List.of(listingId(11), listingId(12), listingId(2)), ids(offers(-1, -1, "", 4, false)), "sold asc");
    }

    @Test
    void theMigrationIndexesTheSearchOnceAndReusesAnEquivalentIndex() throws Exception {
        try (Connection connection = this.dataSource.getConnection()) {
            assertTrue(indexExists(connection, "marketplace_items", "idx_marketplace_items_state_timestamp_price"));
            assertTrue(indexExists(connection, "marketplace_items", "idx_marketplace_items_state_sold_timestamp"));
            assertTrue(indexExists(connection, "items_base", "idx_items_base_sprite_id"));

            int indexes = indexCount(connection);
            runIndexMigration(connection);
            assertEquals(indexes, indexCount(connection), "applying the migration again adds nothing");

            execute(connection, "ALTER TABLE items_base DROP INDEX idx_items_base_sprite_id");
            execute(connection, "ALTER TABLE items_base ADD INDEX custom_sprite_lookup (sprite_id, id)");
            runIndexMigration(connection);
            assertFalse(
                    indexExists(connection, "items_base", "idx_items_base_sprite_id"),
                    "an index with the same leading columns is reused, not duplicated");
        }
    }

    @Test
    void exposesEveryColumnTheOfferReads() throws Exception {
        MarketplaceOffersQuery.Request request = new MarketplaceOffersQuery.Request(-1, -1, "", 1, false);
        Set<String> labels = new HashSet<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(MarketplaceOffersQuery.sql(request))) {
            bind(statement, request);
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    labels.add(metadata.getColumnLabel(column).toLowerCase(Locale.ROOT));
                }
            }
        }

        assertTrue(
                labels.containsAll(Set.of(
                        "id",
                        "item_id",
                        "price",
                        "timestamp",
                        "sold_timestamp",
                        "state",
                        "base_item_id",
                        "ltd_data",
                        "avg",
                        "number",
                        "minprice")),
                () -> "columns: " + labels);
    }

    private List<Offer> offers(int minPrice, int maxPrice, String search, int sort, boolean combineUniques)
            throws Exception {
        MarketplaceOffersQuery.Request request =
                new MarketplaceOffersQuery.Request(minPrice, maxPrice, search, sort, combineUniques);
        List<Offer> offers = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(MarketplaceOffersQuery.sql(request))) {
            bind(statement, request);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int furni = rows.getInt("base_item_id");
                    if (!FIXTURE_FURNI.contains(furni)) {
                        continue;
                    }
                    offers.add(new Offer(
                            rows.getInt("id"),
                            furni,
                            rows.getString("ltd_data"),
                            rows.getInt("minPrice"),
                            rows.getInt("number"),
                            rows.getInt("avg"),
                            rows.getInt("sold_count_today")));
                }
            }
        }
        return offers;
    }

    private void bind(PreparedStatement statement, MarketplaceOffersQuery.Request request) throws Exception {
        MarketplaceOffersQuery.bind(
                statement, request, this.now, MarketPlace.MINIMUM_LISTING_PRICE, MarketPlace.MAXIMUM_LISTING_PRICE);
    }

    private static List<Integer> ids(List<Offer> offers) {
        return offers.stream().map(Offer::id).toList();
    }

    private void loadFixture(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet clock = statement.executeQuery("SELECT UNIX_TIMESTAMP(), UNIX_TIMESTAMP(CURDATE())")) {
            clock.next();
            this.now = (int) clock.getLong(1);
            this.startOfDay = (int) clock.getLong(2);
        }
        int page = catalogPage(connection);

        furni(connection, SOFA, "Test Sofa");
        furni(connection, THRONE, "Test Throne");
        furni(connection, LAMP, "Test Lamp");

        catalogRow(connection, page, SOFA, "sofa_page_a");
        catalogRow(connection, page, SOFA, "sofa_page_b");
        catalogRow(connection, page, THRONE, "throne_page");

        int listedAt = this.now - 100;
        listing(connection, 1, SOFA, "0:0", 50, OPEN, listedAt, 0);
        listing(connection, 3, SOFA, "0:0", 30, OPEN, listedAt, 0);
        listing(connection, 2, SOFA, "0:0", 30, OPEN, listedAt, 0);
        listing(connection, 4, SOFA, "0:0", 45, SOLD, listedAt, this.startOfDay + 60);
        listing(connection, 5, SOFA, "0:0", 45, SOLD, listedAt, this.startOfDay - 3600);
        listing(connection, 6, SOFA, "0:0", 5, OPEN, this.now - 200_000, 0);
        listing(connection, 11, THRONE, "1:5", 500, OPEN, listedAt, 0);
        listing(connection, 12, THRONE, "2:5", 400, OPEN, listedAt, 0);
        listing(connection, 21, LAMP, "0:0", 10, OPEN, listedAt, 0);
    }

    private static int itemId(int fixture) {
        return 1_910_000_000 + fixture;
    }

    private static int listingId(int fixture) {
        return 1_920_000_000 + fixture;
    }

    private static int catalogPage(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet page = statement.executeQuery("SELECT MIN(id) FROM catalog_pages")) {
            if (page.next() && page.getInt(1) > 0) {
                return page.getInt(1);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO catalog_pages (caption) VALUES ('Marketplace fixture')",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.executeUpdate();
            try (ResultSet key = insert.getGeneratedKeys()) {
                key.next();
                return key.getInt(1);
            }
        }
    }

    private static void furni(Connection connection, int id, String publicName) throws Exception {
        execute(
                connection,
                "INSERT INTO items_base (id, sprite_id, public_name, item_name) VALUES (?, ?, ?, ?)",
                id,
                id,
                publicName,
                "fixture_" + id);
    }

    private static void catalogRow(Connection connection, int page, int furni, String name) throws Exception {
        execute(
                connection,
                "INSERT INTO catalog_items (item_ids, page_id, catalog_name) VALUES (?, ?, ?)",
                String.valueOf(furni),
                page,
                name);
    }

    private static void listing(
            Connection connection,
            int fixture,
            int furni,
            String limitedData,
            int price,
            int state,
            int listedAt,
            int soldAt)
            throws Exception {
        execute(
                connection,
                "INSERT INTO items (id, user_id, item_id, limited_data) VALUES (?, 1, ?, ?)",
                itemId(fixture),
                furni,
                limitedData);
        execute(
                connection,
                "INSERT INTO marketplace_items (id, item_id, user_id, price, timestamp, sold_timestamp, state)"
                        + " VALUES (?, ?, 1, ?, ?, ?, ?)",
                listingId(fixture),
                itemId(fixture),
                price,
                listedAt,
                soldAt,
                state);
    }

    private static void runIndexMigration(Connection connection) throws Exception {
        String executable = Files.readString(INDEX_MIGRATION)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        try (Statement statement = connection.createStatement()) {
            for (String sql : executable.split(";\\s*")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static boolean indexExists(Connection connection, String table, String index) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT COUNT(*) FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    private static int indexCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(DISTINCT TABLE_NAME, INDEX_NAME)"
                        + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME IN ('marketplace_items', 'items_base')")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
