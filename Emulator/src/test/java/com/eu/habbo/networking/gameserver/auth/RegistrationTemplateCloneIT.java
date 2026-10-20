package com.eu.habbo.networking.gameserver.auth;

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
 * A new player's room cloned from a template: {@code room_templates_items.wired_data} is NULL by default while
 * {@code items.wired_data} is NOT NULL, so a template furni without wired data used to leave the room empty.
 */
class RegistrationTemplateCloneIT {
    private static final int TEMPLATE = 4;
    private static final int USER = 12;
    private static final int ROOM = 77;

    @Test
    void templateFurniWithoutWiredDataReachTheNewRoom() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("registration_template_clone")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO room_templates (template_id, model) VALUES (" + TEMPLATE + ", 'model_a')");
                statement.executeUpdate("INSERT INTO room_templates_items (template_id, item_id, wall_pos, x, y, z,"
                        + " rot, extra_data, wired_data) VALUES (" + TEMPLATE + ", 1, '', 3, 4, 0, 2, '0', NULL)");
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement clone =
                            connection.prepareStatement(RegistrationSupport.CLONE_TEMPLATE_ITEMS_SQL)) {
                clone.setInt(1, USER);
                clone.setInt(2, ROOM);
                clone.setInt(3, TEMPLATE);
                assertEquals(1, clone.executeUpdate());
            }

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet set = statement.executeQuery(
                            "SELECT x, y, wired_data FROM items WHERE room_id = " + ROOM + " AND user_id = " + USER)) {
                assertTrue(set.next());
                assertEquals(3, set.getInt("x"));
                assertEquals(4, set.getInt("y"));
                assertEquals("", set.getString("wired_data"));
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
