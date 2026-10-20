package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A furni chest on a hotel that came from Arcturus, whose {@code items} text columns allow NULL while the
 * chest table keeps them NOT NULL: depositing such an item used to roll back ("Column 'wired_data' cannot be
 * null") and the player saw the trade cancelled. The item now goes in with the chest's defaults and comes back
 * out whole.
 */
class DatabaseChestStoreNullableItemsIT {
    private static final int OWNER = 7;
    private static final int CHEST = 900;
    private static final int ITEM = 5001;

    @Test
    void anItemWithNullColumnsGoesIntoTheChestAndComesBackOut() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("chest_nullable_items")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE items MODIFY extra_data VARCHAR(1024) NULL DEFAULT NULL,"
                        + " MODIFY wired_data VARCHAR(10000) NULL DEFAULT NULL,"
                        + " MODIFY limited_data VARCHAR(10) NULL DEFAULT NULL,"
                        + " MODIFY guild_id INT(11) NULL DEFAULT NULL");
                statement.executeUpdate("INSERT INTO items (id, user_id, room_id, item_id, extra_data, wired_data,"
                        + " limited_data, guild_id) VALUES (" + ITEM + ", " + OWNER
                        + ", 0, 1, NULL, NULL, NULL, NULL)");
            }

            DatabaseChestStore store = new DatabaseChestStore(dataSource::getConnection);
            store.createChest(CHEST, ChestStorage.KIND_FURNI, 0, 0, "{}");

            assertEquals(List.of(ITEM), store.depositItems(CHEST, OWNER, List.of(ITEM), 1L));
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet stored = statement.executeQuery("SELECT extra_data, wired_data, limited_data, guild_id"
                            + " FROM wired_chest_items WHERE id = " + ITEM + " AND chest_id = " + CHEST)) {
                assertTrue(stored.next(), "the item is in the chest");
                assertEquals("", stored.getString("extra_data"));
                assertEquals("", stored.getString("wired_data"));
                assertEquals("0:0", stored.getString("limited_data"));
                assertEquals(0, stored.getInt("guild_id"));
            }
            assertFalse(itemExists(dataSource), "the item left the inventory");
            assertEquals(1, store.loadItems(CHEST).size());

            Map<Integer, Integer> moved = store.withdrawItems(CHEST, OWNER, List.of(ITEM));
            assertEquals(Map.of(ITEM, ITEM), moved, "the item comes back under its own id");
            assertTrue(itemExists(dataSource), "the item is back in the inventory");
            assertTrue(store.loadItems(CHEST).isEmpty());
        }
    }

    private static boolean itemExists(HikariDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT 1 FROM items WHERE id = " + ITEM + " AND user_id = " + OWNER + " AND room_id = 0")) {
            return set.next();
        }
    }

    private static void requireDocker() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI");
            }
            assumeTrue(false, "Docker/Testcontainers not available - skipping DB integration test");
        }
    }
}
