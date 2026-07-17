package com.eu.habbo.database.migration;

import com.eu.habbo.database.TestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@link MigrationRunner} (Flyway + the packaged V1..Vn migrations)
 * against a throwaway MariaDB and asserts it produces the expected schema. This
 * is the executable version of the schema-equivalence gate: if a migration is
 * broken (as the V4 ROW_FORMAT bug was), this fails instead of shipping.
 */
class MigrationRunnerIT {

    /** Skips the test (rather than failing) when Testcontainers can't reach Docker. */
    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable t) {
            assumeTrue(false, "Docker/Testcontainers not available — skipping DB integration test: " + t.getMessage());
        }
    }

    @Test
    void freshDatabaseMigratesToTheFullPolarisSchema() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_fresh")) {
            // Empty DB -> EMPTY state.
            assertEquals(SchemaPreflight.State.EMPTY, SchemaPreflight.detect(ds));

            MigrationRunner.migrate(ds);

            // Baseline table and a representative Polaris-only table both exist.
            assertTrue(tableExists(ds, "users"), "Arc baseline table users must exist");
            assertTrue(tableExists(ds, "permission_ranks"), "Polaris table permission_ranks must exist");
            assertTrue(tableExists(ds, "wired_emulator_settings"), "Polaris table wired_emulator_settings must exist");

            // Polaris-added column on a shared table.
            assertTrue(columnExists(ds, "users", "auth_ticket_expires_at"),
                    "Polaris column users.auth_ticket_expires_at must exist");

            // V4 engine conversion took effect.
            assertEquals("InnoDB", tableEngine(ds, "marketplace_items"),
                    "marketplace_items must be InnoDB after V4");

            // Now MANAGED, and a second migrate is a no-op (Flyway history).
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);
        }
    }

    @Test
    void existingUnmanagedInstallIsRecognisedBaselinedAndAdopted() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_existing")) {
            // Build the full Polaris schema, then drop the Flyway history to simulate an
            // existing Arc/Polaris install that predates the migration system (has the
            // schema, was never Flyway-managed).
            MigrationRunner.migrate(ds);
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("DROP TABLE `flyway_schema_history`");
            }

            // It must be recognised (not refused) and adopted: baseline at V1, then the
            // guarded V2..Vn no-op since everything is already present.
            assertEquals(SchemaPreflight.State.RECOGNISED_EXISTING, SchemaPreflight.detect(ds));
            MigrationRunner.migrate(ds);
            assertEquals(SchemaPreflight.State.MANAGED, SchemaPreflight.detect(ds));
            assertTrue(tableExists(ds, "flyway_schema_history"), "adoption must create the Flyway history");
        }
    }

    @Test
    void unknownNonEmptyDatabaseIsRefusedWithoutMutation() throws Exception {
        requireDocker();
        try (HikariDataSource ds = TestDatabase.freshDatabase("mig_unknown")) {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE `something_unrelated` (`id` INT PRIMARY KEY)");
            }
            assertEquals(SchemaPreflight.State.UNKNOWN, SchemaPreflight.detect(ds));

            try {
                MigrationRunner.migrate(ds);
                org.junit.jupiter.api.Assertions.fail("expected refusal");
            } catch (MigrationException expected) {
                // no flyway history was created
                assertTrue(!tableExists(ds, "flyway_schema_history"), "unknown DB must not be touched");
            }
        }
    }

    private static boolean tableExists(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(HikariDataSource ds, String table, String column) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            st.setString(1, table);
            st.setString(2, column);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String tableEngine(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             var st = c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
