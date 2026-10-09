package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Runs the per-type pet commands migration on a migrated MariaDB, including a table full of copies. */
class PetCommandsPerTypeMigrationIT {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260924210000__pet_commands_per_type.sql");

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
    void everyPetTypeCanBeTrainedAndEachPairIsStoredOnce() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("pet_commands_per_type");
                Connection connection = dataSource.getConnection()) {
            MigrationRunner.migrate(dataSource);

            assertEquals(14, count(connection, "SELECT COUNT(*) FROM pet_commands WHERE pet_id = 33"));
            assertEquals(
                    0,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM pet_actions a WHERE NOT EXISTS"
                                    + " (SELECT 1 FROM pet_commands c WHERE c.pet_id = a.pet_type)"),
                    "every pet type has commands");
            assertEquals(
                    0,
                    count(
                            connection,
                            "SELECT COUNT(*) FROM pet_commands c WHERE NOT EXISTS"
                                    + " (SELECT 1 FROM pet_commands_data d WHERE d.command_id = c.command_id)"),
                    "only known commands");

            int migrated = count(connection, "SELECT COUNT(*) FROM pet_commands");
            runMigration(connection);
            assertEquals(migrated, count(connection, "SELECT COUNT(*) FROM pet_commands"), "a second run adds nothing");

            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE pet_commands DROP PRIMARY KEY");
                statement.execute("INSERT INTO pet_commands (pet_id, command_id) VALUES (12, 36), (12, 36), (99, 1)");
            }
            runMigration(connection);

            assertEquals(
                    1, count(connection, "SELECT COUNT(*) FROM pet_commands WHERE pet_id = 12 AND command_id = 36"));
            assertEquals(
                    1,
                    count(connection, "SELECT COUNT(*) FROM pet_commands WHERE pet_id = 99"),
                    "a hotel's own row stays");
            assertTrue(count(
                            connection,
                            "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()"
                                    + " AND TABLE_NAME = 'pet_commands' AND CONSTRAINT_TYPE = 'PRIMARY KEY'")
                    == 1);
        }
    }

    private static void runMigration(Connection connection) throws Exception {
        String executable = Files.readString(MIGRATION)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        try (Statement statement = connection.createStatement()) {
            for (String sql : executable.split(";\s*")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
