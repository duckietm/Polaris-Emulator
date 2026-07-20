package com.eu.habbo.database;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;

class HotQueryProjectionIT {

    @Test
    void pinnedHotQueriesExecuteAgainstTheRuntimeSchema() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("hot_query_projection")) {
            MigrationRunner.migrate(dataSource);

            execute(dataSource, sql(MarketPlace.class, "PURCHASE_OFFER_SQL"), 0);
            execute(dataSource, sql(MarketPlace.class, "PURCHASE_ITEM_SQL"), 0);
            execute(dataSource, sql(ItemManager.class, "BASE_ITEMS_SQL"));
            execute(dataSource, sql(RoomManager.class, "PUBLIC_ROOMS_SQL"), "1", "1");
            execute(dataSource, sql(RoomManager.class, "ROOM_IDS_BY_OWNER_NAME_SQL"), "nobody");
            execute(dataSource, sql(RoomManager.class, "ROOMS_BY_OWNER_ID_SQL"), 0);
        }
    }

    private static void execute(HikariDataSource dataSource, String sql, Object... parameters) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(1);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeQuery();
        }
    }

    private static String sql(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
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
