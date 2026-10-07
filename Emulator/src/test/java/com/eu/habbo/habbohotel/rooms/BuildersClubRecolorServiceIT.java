package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The recolor service's queries against the real schema. */
class BuildersClubRecolorServiceIT {

    @Test
    void readsTheBuildersClubTablesAndStoresTheNewColour() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("bc_recolor")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO catalog_items_bc (item_ids, page_id, catalog_name, order_number)"
                        + " VALUES ('990013', 1, 'bc_cone*13', 1)");
                statement.executeUpdate(
                        "INSERT INTO items (id, user_id, room_id, item_id) VALUES (990101, 5, 77, 990012),"
                                + " (990102, 5, 77, 990012), (990103, 5, 78, 990012), (990104, 5, 77, 990012)");
                statement.executeUpdate("INSERT INTO builders_club_items (item_id, user_id, room_id) VALUES"
                        + " (990101, 5, 77), (990103, 5, 78), (990104, 5, 0)");

                assertTrue(BuildersClubRecolorService.offeredInBuildersClub(connection, 990013));
                assertFalse(BuildersClubRecolorService.offeredInBuildersClub(connection, 990014));
                // 990104 stands in room 77 although its tracking row still says room 0.
                assertEquals(Set.of(990101, 990104), BuildersClubRecolorService.buildersClubItemsIn(connection, 77));

                HabboItem placed = mock(HabboItem.class);
                when(placed.getId()).thenReturn(990101);
                HabboItem elsewhere = mock(HabboItem.class);
                when(elsewhere.getId()).thenReturn(990103);
                assertEquals(
                        List.of(placed),
                        BuildersClubRecolorService.store(connection, List.of(placed, elsewhere), 990013, 77));

                try (ResultSet set = statement.executeQuery(
                        "SELECT id, item_id FROM items WHERE id IN (990101, 990102, 990103) ORDER BY id")) {
                    assertTrue(set.next());
                    assertEquals(990013, set.getInt("item_id"));
                    assertTrue(set.next());
                    assertEquals(990012, set.getInt("item_id"));
                    assertTrue(set.next());
                    assertEquals(990012, set.getInt("item_id"));
                }
            }
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
