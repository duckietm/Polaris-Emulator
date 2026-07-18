package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationHistoryRepositoryTest {
    @Test
    void recognizesOnlyACompletePolarisSchemaAndFindsHistoryTable() throws Exception {
        RecordingJdbc complete = new RecordingJdbc(Set.of(
                "users", "rooms", "items", "emulator_settings", "schema_migrations"));
        MigrationHistoryRepository repository = new MigrationHistoryRepository(complete.connection());

        assertTrue(repository.isRecognizablePolarisSchema());
        assertTrue(repository.historyTableExists());

        RecordingJdbc incomplete = new RecordingJdbc(Set.of("users", "rooms", "items"));
        repository = new MigrationHistoryRepository(incomplete.connection());

        assertFalse(repository.isRecognizablePolarisSchema());
        assertFalse(repository.historyTableExists());
    }

    @Test
    void createsTheHistoryTableWithTheStableSchema() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(Set.of());
        MigrationHistoryRepository repository = new MigrationHistoryRepository(jdbc.connection());

        repository.ensureHistoryTable();

        String sql = jdbc.executedStatements.getFirst();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS schema_migrations"));
        assertTrue(sql.contains("version INT UNSIGNED NOT NULL PRIMARY KEY"));
        assertTrue(sql.contains("checksum_sha256 CHAR(64) NOT NULL"));
        assertTrue(sql.contains("execution_ms BIGINT UNSIGNED NOT NULL"));
        assertTrue(sql.contains("ENGINE=InnoDB"));
        assertTrue(sql.contains("COLLATE=utf8mb4_unicode_ci"));
    }

    @Test
    void writesTheHistoricalBaselineThroughAPreparedStatement() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(Set.of());
        MigrationHistoryRepository repository = new MigrationHistoryRepository(jdbc.connection());

        repository.baselineAt027();

        RecordedPrepared prepared = jdbc.prepared.getFirst();
        assertTrue(prepared.sql.startsWith("INSERT INTO schema_migrations"));
        assertEquals(27, prepared.bindings.get(1));
        assertEquals("historical baseline", prepared.bindings.get(2));
        assertEquals("<< baseline >>", prepared.bindings.get(3));
        assertEquals("0".repeat(64), prepared.bindings.get(4));
        assertEquals(0L, prepared.bindings.get(5));
        assertEquals(1, prepared.updates);
    }

    @Test
    void loadsAppliedMigrationsInVersionOrder() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(Set.of("schema_migrations"));
        jdbc.queryRows = List.of(
                row(27, "historical baseline", "<< baseline >>", "0".repeat(64),
                        Timestamp.from(Instant.parse("2026-07-14T12:00:00Z")), 0L),
                row(28, "next", "028_next.sql", "a".repeat(64),
                        Timestamp.from(Instant.parse("2026-07-14T12:01:00Z")), 15L));
        MigrationHistoryRepository repository = new MigrationHistoryRepository(jdbc.connection());

        List<AppliedMigration> applied = repository.loadApplied();

        assertEquals(List.of(27, 28), applied.stream().map(AppliedMigration::version).toList());
        assertEquals("028_next.sql", applied.get(1).scriptName());
        assertEquals(15L, applied.get(1).executionMs());
        assertTrue(jdbc.prepared.getFirst().sql.contains("ORDER BY version ASC"));
    }

    @Test
    void recordsAppliedMigrationAndClampsClockAnomaliesToZero() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc(Set.of("schema_migrations"));
        MigrationHistoryRepository repository = new MigrationHistoryRepository(jdbc.connection());
        MigrationDescriptor migration = new MigrationDescriptor(
                28, "next", "028_next.sql", "SELECT 1", "a".repeat(64));

        repository.recordApplied(migration, -4L);

        RecordedPrepared prepared = jdbc.prepared.getFirst();
        assertEquals(28, prepared.bindings.get(1));
        assertEquals("next", prepared.bindings.get(2));
        assertEquals("028_next.sql", prepared.bindings.get(3));
        assertEquals("a".repeat(64), prepared.bindings.get(4));
        assertEquals(0L, prepared.bindings.get(5));
        assertEquals(1, prepared.updates);
    }

    private static Map<String, Object> row(
            int version,
            String description,
            String scriptName,
            String checksum,
            Timestamp installedOn,
            long executionMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", version);
        row.put("description", description);
        row.put("script_name", scriptName);
        row.put("checksum_sha256", checksum);
        row.put("installed_on", installedOn);
        row.put("execution_ms", executionMs);
        return row;
    }

    private static final class RecordingJdbc implements InvocationHandler {
        private final Set<String> tables;
        private final List<String> executedStatements = new ArrayList<>();
        private final List<RecordedPrepared> prepared = new ArrayList<>();
        private List<Map<String, Object>> queryRows = List.of();

        private RecordingJdbc(Set<String> tables) {
            this.tables = tables;
        }

        private Connection connection() {
            return proxy(Connection.class, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getCatalog" -> "polaris";
                case "getMetaData" -> metadata();
                case "createStatement" -> statement();
                case "prepareStatement" -> prepared((String) args[0]);
                case "close" -> null;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private DatabaseMetaData metadata() {
            return proxy(DatabaseMetaData.class, (ignored, method, args) -> {
                if (method.getName().equals("getTables")) {
                    List<Map<String, Object>> rows = tables.stream()
                            .map(table -> Map.<String, Object>of("TABLE_NAME", table))
                            .toList();
                    return resultSet(rows);
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Statement statement() {
            return proxy(Statement.class, (ignored, method, args) -> {
                if (method.getName().equals("executeUpdate") || method.getName().equals("execute")) {
                    executedStatements.add((String) args[0]);
                    return method.getReturnType() == boolean.class ? false : 0;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement prepared(String sql) {
            RecordedPrepared recording = new RecordedPrepared(sql);
            prepared.add(recording);
            return proxy(PreparedStatement.class, (ignored, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2) {
                    recording.bindings.put((Integer) args[0], args[1]);
                    return null;
                }
                if (name.equals("executeUpdate")) {
                    recording.updates++;
                    return 1;
                }
                if (name.equals("executeQuery")) return resultSet(queryRows);
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static final class RecordedPrepared {
        private final String sql;
        private final Map<Integer, Object> bindings = new LinkedHashMap<>();
        private int updates;

        private RecordedPrepared(String sql) {
            this.sql = sql;
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        final int[] index = {-1};
        return proxy(ResultSet.class, (ignored, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getString" -> String.valueOf(rows.get(index[0]).get((String) args[0]));
            case "getInt" -> ((Number) rows.get(index[0]).get((String) args[0])).intValue();
            case "getLong" -> ((Number) rows.get(index[0]).get((String) args[0])).longValue();
            case "getTimestamp" -> rows.get(index[0]).get((String) args[0]);
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
