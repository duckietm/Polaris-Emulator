package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.items.WebApiBoxSql;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A {@code wf_} item whose base row says interaction {@code default} takes its interaction from its
 * name on load; the queries that look such items up in the database must find it the same way.
 */
class WiredItemNameFallbackSqlIT {
    private static final int OWNER = 930001;
    private static final int ROOM = 930011;
    private static final int OTHER_ROOM = 930012;

    @Test
    void itemsWithInteractionDefaultAreFoundByName() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("wired_item_name_fallback")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO items_base (id, sprite_id, public_name, item_name, interaction_type) VALUES
                            (930101, 930101, 'api', 'wf_xtra_var_web_api', 'default'),
                            (930102, 930102, 'global', 'wf_xtra_text_output_global', 'default'),
                            (930103, 930103, 'user var', 'wf_var_user', 'default'),
                            (930104, 930104, 'chair', 'chair_plain', 'default')
                        """);
                statement.executeUpdate("INSERT INTO users (id, username, password, ip_register, ip_current) VALUES ("
                        + OWNER + ", 'owner', '!', '127.0.0.1', '127.0.0.1')");
                statement.executeUpdate("INSERT INTO rooms (id, owner_id, owner_name, name) VALUES (" + ROOM + ", "
                        + OWNER + ", 'owner', 'B'), (" + OTHER_ROOM + ", " + OWNER + ", 'owner', 'A')");
                statement.executeUpdate("""
                        INSERT INTO items (id, user_id, room_id, item_id, wired_data, limited_data) VALUES
                            (930201, 930001, 930011, 930101, '', '0:0'),
                            (930202, 930001, 930011, 930102,
                             '{"placeholderName":"score","mode":0,"value":"7","sourceRoomId":0}', '0:0'),
                            (930203, 930001, 930011, 930103, '{"variableName":"points"}', '0:0'),
                            (930204, 930001, 930011, 930104, '', '0:0')
                        """);
            }

            List<WiredGlobalPlaceholderSupport.SharedPlaceholder> shared =
                    WiredGlobalPlaceholderSupport.loadShared(dataSource, OWNER, OTHER_ROOM);
            assertEquals(1, shared.size());
            assertEquals("score", shared.get(0).name());

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                List<Integer> copied = new ArrayList<>();
                try (ResultSet set = statement.executeQuery("SELECT id FROM items WHERE room_id = " + ROOM
                        + " AND item_id NOT IN (" + WebApiBoxSql.BASE_ITEM_IDS + ") ORDER BY id")) {
                    while (set.next()) {
                        copied.add(set.getInt(1));
                    }
                }
                assertFalse(copied.contains(930201), "a room copy must leave the box out");
                assertEquals(List.of(930202, 930203, 930204), copied);

                try (ResultSet set = statement.executeQuery("SELECT items.id, "
                        + WiredVariableReferenceSupport.DEFINITION_TYPE_SQL + " AS type FROM items "
                        + "INNER JOIN items_base ON items.item_id = items_base.id WHERE items.room_id = " + ROOM
                        + " AND (items_base.interaction_type IN " + WiredVariableReferenceSupport.DEFINITION_TYPES
                        + " OR items_base.item_name IN " + WiredVariableReferenceSupport.DEFINITION_TYPES + ")")) {
                    List<String> found = new ArrayList<>();
                    while (set.next()) {
                        found.add(set.getInt(1) + ":" + set.getString(2));
                    }
                    assertEquals(List.of("930203:wf_var_user"), found);
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
