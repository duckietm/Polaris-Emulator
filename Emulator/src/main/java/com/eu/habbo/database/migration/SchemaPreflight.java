package com.eu.habbo.database.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Inspects a database before migration and classifies it into one of the states
 * the adoption state machine handles. This is the safety gate that stops Polaris
 * from baselining and mutating a schema it does not recognise (for example a
 * mistyped database name).
 *
 * <p>The recognition check deliberately uses a compact Arc-family signature
 * instead of requiring an exact pristine dump. Real hotels commonly add plugin
 * tables or harmless custom columns, and those should remain a one-start upgrade.
 * Requiring several characteristic tables and columns still avoids blessing an
 * unrelated database selected by mistake.
 */
public final class SchemaPreflight {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaPreflight.class);

    /** Characteristic Arc/Polaris tables and their stable identity columns.
     * Extra tables and columns are intentionally tolerated. */
    private static final Map<String, Set<String>> REQUIRED_SCHEMA_SIGNATURE = new LinkedHashMap<>();

    static {
        REQUIRED_SCHEMA_SIGNATURE.put("users", Set.of("id", "username", "password", "auth_ticket"));
        REQUIRED_SCHEMA_SIGNATURE.put("rooms", Set.of("id", "owner_id", "model"));
        REQUIRED_SCHEMA_SIGNATURE.put("items", Set.of("id", "user_id", "room_id", "item_id"));
        REQUIRED_SCHEMA_SIGNATURE.put("items_base", Set.of("id", "interaction_type"));
        REQUIRED_SCHEMA_SIGNATURE.put("catalog_pages", Set.of("id", "page_layout"));
        REQUIRED_SCHEMA_SIGNATURE.put("emulator_settings", Set.of("key", "value"));
        REQUIRED_SCHEMA_SIGNATURE.put("permissions", Set.of("id", "rank_name"));
        REQUIRED_SCHEMA_SIGNATURE.put("users_currency", Set.of("user_id", "type", "amount"));
    }

    public enum State {
        /** No user tables at all — a brand-new database. Apply V1 then V2..Vn. */
        EMPTY,
        /** Already has flyway_schema_history — validate and apply pending. */
        MANAGED,
        /** Non-empty, no Flyway history, but has the required invariant tables —
         *  a recognised Arc/Polaris install to baseline at V1 then migrate. */
        RECOGNISED_EXISTING,
        /** Non-empty, no Flyway history, missing required invariants — refuse. */
        UNKNOWN
    }

    private SchemaPreflight() {
    }

    public static State detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, "flyway_schema_history")) {
                return State.MANAGED;
            }

            int userTableCount = userTableCount(connection);
            if (userTableCount == 0) {
                return State.EMPTY;
            }

            Set<String> missing = new LinkedHashSet<>();
            for (Map.Entry<String, Set<String>> entry : REQUIRED_SCHEMA_SIGNATURE.entrySet()) {
                String table = entry.getKey();
                if (!tableExists(connection, table)) {
                    missing.add(table);
                    continue;
                }
                for (String column : entry.getValue()) {
                    if (!columnExists(connection, table, column)) {
                        missing.add(table + "." + column);
                    }
                }
            }

            if (missing.isEmpty()) {
                return State.RECOGNISED_EXISTING;
            }

            LOGGER.warn("[migrate] Database is non-empty but missing required invariant tables {} — refusing to touch it.", missing);
            return State.UNKNOWN;
        } catch (SQLException e) {
            throw new MigrationException("Could not inspect the database before migration", e);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static int userTableCount(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
