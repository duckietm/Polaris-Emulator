package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Runs the room linker repair on a migrated MariaDB against the values hotels actually carry. */
class WiredRoomLinkerInteractionRepairIT {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260923210000__wired_room_linker_interaction.sql");
    private static final int FROM_TELEPORT_KEY = 1_900_000_101;
    private static final int FROM_DEFAULT = 1_900_000_102;
    private static final int ALREADY_RIGHT = 1_900_000_103;

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

    @Test
    void everyRoomLinkerRowEndsOnTheLinkerInteractionAndKeepsItsName() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("room_linker_repair");
                Connection connection = dataSource.getConnection()) {
            MigrationRunner.migrate(dataSource);
            insert(connection, FROM_TELEPORT_KEY, "Teleport Tile", "teletile");
            insert(connection, FROM_DEFAULT, "Linker", "default");
            insert(connection, ALREADY_RIGHT, "Already right", "wf_room_linker");

            assertEquals(2, runMigration(connection), "only the two diverging rows change");
            assertEquals("wf_room_linker", interaction(connection, FROM_TELEPORT_KEY));
            assertEquals("wf_room_linker", interaction(connection, FROM_DEFAULT));
            assertEquals("wf_room_linker", interaction(connection, ALREADY_RIGHT));
            assertEquals("Teleport Tile", publicName(connection, FROM_TELEPORT_KEY), "the name stays the hotel's");

            assertEquals(0, runMigration(connection), "a second run changes nothing");
        }
    }

    private static void insert(Connection connection, int id, String publicName, String interaction) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items_base (id, sprite_id, public_name, item_name, interaction_type)"
                        + " VALUES (?, ?, ?, 'wf_room_linker', ?)")) {
            statement.setInt(1, id);
            statement.setInt(2, id);
            statement.setString(3, publicName);
            statement.setString(4, interaction);
            statement.executeUpdate();
        }
    }

    /** Runs the migration's statements and returns the rows its update changed. */
    private static int runMigration(Connection connection) throws Exception {
        String executable = Files.readString(MIGRATION)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        int changed = 0;
        try (Statement statement = connection.createStatement()) {
            for (String sql : executable.split(";\\s*")) {
                if (sql.isBlank()) {
                    continue;
                }
                statement.execute(sql);
                if (sql.stripLeading().startsWith("UPDATE")) {
                    changed += statement.getUpdateCount();
                }
            }
        }
        return changed;
    }

    private static String interaction(Connection connection, int id) throws Exception {
        return column(connection, id, "interaction_type");
    }

    private static String publicName(Connection connection, int id) throws Exception {
        return column(connection, id, "public_name");
    }

    private static String column(Connection connection, int id, String column) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT " + column + " FROM items_base WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }
}
