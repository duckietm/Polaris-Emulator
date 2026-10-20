package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * :setroom_template on a hotel that came from Arcturus, whose {@code items} text columns allow NULL while
 * {@code room_templates_items} keeps {@code wall_pos} and {@code extra_data} NOT NULL: one such furni used to fail
 * the whole copy.
 */
class RoomTemplateNullableColumnsIT {
    private static final int ROOM = 50;
    private static final int TEMPLATE = 3;

    @Test
    void furniWithNullColumnsAreCopiedIntoTheTemplate() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("template_nullable_items")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO room_templates (template_id, model) VALUES (" + TEMPLATE + ", 'model_a')");
                statement.execute("ALTER TABLE items MODIFY wall_pos VARCHAR(20) NULL DEFAULT NULL,"
                        + " MODIFY extra_data VARCHAR(1024) NULL DEFAULT NULL,"
                        + " MODIFY wired_data VARCHAR(10000) NULL DEFAULT NULL");
                statement.executeUpdate("INSERT INTO items (id, user_id, room_id, item_id, wall_pos, extra_data,"
                        + " wired_data) VALUES (6001, 1, " + ROOM + ", 1, NULL, NULL, NULL),"
                        + " (6002, 1, " + ROOM + ", 1, '', '1', '{}')");
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement copy = connection.prepareStatement(SetRoomTemplateCommand.COPY_ROOM_ITEMS_SQL)) {
                copy.setInt(1, TEMPLATE);
                copy.setInt(2, ROOM);
                assertEquals(2, copy.executeUpdate());
            }

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT wall_pos, extra_data, wired_data FROM"
                            + " room_templates_items WHERE template_id = " + TEMPLATE + " ORDER BY extra_data")) {
                assertTrue(set.next());
                assertEquals("", set.getString("wall_pos"));
                assertEquals("", set.getString("extra_data"));
                assertEquals("", set.getString("wired_data"));
                assertTrue(set.next());
                assertEquals("1", set.getString("extra_data"));
                assertEquals("{}", set.getString("wired_data"));
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
