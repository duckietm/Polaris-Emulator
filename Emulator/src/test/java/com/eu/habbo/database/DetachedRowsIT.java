package com.eu.habbo.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import javax.sql.rowset.CachedRowSet;
import org.junit.jupiter.api.Test;

/**
 * A user's row is copied before the Habbo is built from it (see DetachedRows). The copy must read like
 * the live row for every column of {@code users}, with the getters HabboInfo and Habbo use.
 */
class DetachedRowsIT {
    @Test
    void aCopiedUserRowReadsLikeTheLiveOne() throws Exception {
        requireDocker();
        try (HikariDataSource dataSource = TestDatabase.freshDatabase("detached_rows")) {
            MigrationRunner.migrate(dataSource);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (id, username, password, mail, account_created, motto, look, gender,
                            `rank`, credits, online, auth_ticket, ip_register, ip_current, mail_verified)
                        VALUES (950001, 'détaché', '!', 'd@example.invalid', 1700000000, 'motto ✓', 'hd-180-1',
                            'F', 3, 1234, '1', 'ticket', '127.0.0.1', '127.0.0.2', 1)
                        """);
            }

            CachedRowSet copy;
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                statement.setInt(1, 950001);
                try (ResultSet set = statement.executeQuery()) {
                    copy = DetachedRows.copy(set);
                }
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?");
                    CachedRowSet row = copy) {
                statement.setInt(1, 950001);
                try (ResultSet live = statement.executeQuery()) {
                    assertTrue(live.next());
                    assertTrue(row.next());
                    ResultSetMetaData meta = live.getMetaData();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        String name = meta.getColumnLabel(column);
                        assertEquals(live.getString(name), row.getString(name), name + " as text");
                        if (live.getString(name) == null) {
                            continue;
                        }
                        if (isWholeNumber(meta.getColumnType(column)) || isFlag(live.getString(name))) {
                            assertEquals(live.getInt(name), row.getInt(name), name + " as int");
                            assertEquals(live.getBoolean(name), row.getBoolean(name), name + " as boolean");
                        }
                    }
                    assertFalse(row.next(), "one row");
                }
            }
        }
    }

    private static boolean isWholeNumber(int type) {
        return type == Types.INTEGER
                || type == Types.SMALLINT
                || type == Types.TINYINT
                || type == Types.BIGINT
                || type == Types.BIT
                || type == Types.BOOLEAN;
    }

    /** enum('0','1') columns read as numbers and booleans. */
    private static boolean isFlag(String value) {
        return "0".equals(value) || "1".equals(value);
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
