package com.eu.habbo.database.migrations;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationRunnerTest {
    @Test
    void validateBaselinesRecognizableSchemaAndReportsPendingWithoutExecution() {
        MigrationJdbc jdbc = MigrationJdbc.coreSchema();
        DatabaseMigrationRunner runner = runner(jdbc, migrations("SELECT 28", "SELECT 29"));

        MigrationReport report = runner.run(MigrationMode.VALIDATE);

        assertEquals(List.of(28, 29), report.pendingVersions());
        assertEquals(List.of(), report.appliedVersions());
        assertEquals(27, report.installedVersion());
        assertEquals(29, report.packagedVersion());
        assertEquals(List.of(27), jdbc.historyVersions());
        assertEquals(List.of(), jdbc.executedMigrationStatements);
        assertTrue(jdbc.lockReleased());
    }

    @Test
    void applyRunsAndRecordsInOrderThenBecomesANoOp() {
        MigrationJdbc jdbc = MigrationJdbc.coreSchema();
        DatabaseMigrationRunner runner = runner(jdbc, migrations("SELECT 28", "SELECT 29"));

        MigrationReport first = runner.run(MigrationMode.APPLY);
        MigrationReport second = runner.run(MigrationMode.APPLY);

        assertEquals(List.of(28, 29), first.appliedVersions());
        assertEquals(List.of(), second.appliedVersions());
        assertEquals(29, first.installedVersion());
        assertEquals(29, first.packagedVersion());
        assertEquals(List.of(27, 28, 29), jdbc.historyVersions());
        assertEquals(List.of("SELECT 28", "SELECT 29"), jdbc.executedMigrationStatements);
        assertEquals(2, jdbc.lockAcquireCount);
        assertEquals(2, jdbc.lockReleaseCount);
    }

    @Test
    void rejectsChecksumDriftAndStoredVersionsNewerThanTheCatalog() {
        MigrationJdbc drift = MigrationJdbc.withHistory(
                baseline(), applied(28, "next", "028_next.sql", "f".repeat(64)));
        assertThrows(MigrationValidationException.class,
                () -> runner(drift, migrations("SELECT 28", "SELECT 29")).run(MigrationMode.VALIDATE));
        assertTrue(drift.lockReleased());

        MigrationJdbc newer = MigrationJdbc.withHistory(
                baseline(), applied(30, "future", "030_future.sql", "a".repeat(64)));
        assertThrows(MigrationValidationException.class,
                () -> runner(newer, migrations("SELECT 28", "SELECT 29")).run(MigrationMode.VALIDATE));
        assertTrue(newer.lockReleased());
    }

    @Test
    void rejectsEmptyOrMissingBaselineHistory() {
        MigrationJdbc empty = MigrationJdbc.withHistory();
        assertThrows(MigrationValidationException.class,
                () -> runner(empty, migrations("SELECT 28", "SELECT 29")).run(MigrationMode.VALIDATE));

        MigrationJdbc missing = MigrationJdbc.withHistory(
                applied(28, "next", "028_next.sql", checksum("SELECT 28")));
        assertThrows(MigrationValidationException.class,
                () -> runner(missing, migrations("SELECT 28", "SELECT 29")).run(MigrationMode.VALIDATE));
    }

    @Test
    void doesNotRecordFailedMigrationAndAlwaysReleasesLock() {
        MigrationJdbc jdbc = MigrationJdbc.coreSchema();
        DatabaseMigrationRunner runner = runner(jdbc, migrations("SELECT 28; FAIL 28", "SELECT 29"));

        MigrationExecutionException error = assertThrows(
                MigrationExecutionException.class,
                () -> runner.run(MigrationMode.APPLY));

        assertEquals(28, error.version());
        assertEquals(2, error.statementNumber());
        assertEquals(List.of(27), jdbc.historyVersions());
        assertEquals(List.of("SELECT 28"), jdbc.executedMigrationStatements);
        assertTrue(jdbc.lockReleased());
    }

    @Test
    void refusesToContinueWhenTheNamedLockCannotBeAcquired() {
        MigrationJdbc jdbc = MigrationJdbc.coreSchema();
        jdbc.lockResult = 0;

        assertThrows(MigrationValidationException.class,
                () -> runner(jdbc, migrations("SELECT 28", "SELECT 29")).run(MigrationMode.APPLY));

        assertEquals(0, jdbc.lockReleaseCount);
        assertEquals(List.of(), jdbc.executedMigrationStatements);
    }

    private static DatabaseMigrationRunner runner(MigrationJdbc jdbc, MigrationCatalog catalog) {
        return new DatabaseMigrationRunner(jdbc.connection(), catalog, 10);
    }

    private static MigrationCatalog migrations(String version28, String version29) {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("028_next.sql", version28);
        scripts.put("029_after.sql", version29);
        return MigrationCatalog.fromResources(scripts);
    }

    private static Map<String, Object> baseline() {
        return applied(27, "historical baseline", "<< baseline >>", "0".repeat(64));
    }

    private static Map<String, Object> applied(
            int version,
            String description,
            String scriptName,
            String checksum) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", version);
        row.put("description", description);
        row.put("script_name", scriptName);
        row.put("checksum_sha256", checksum);
        row.put("installed_on", Timestamp.from(Instant.parse("2026-07-14T12:00:00Z")));
        row.put("execution_ms", 1L);
        return row;
    }

    private static String checksum(String sql) {
        return MigrationCatalog.sha256(sql);
    }

    private static final class MigrationJdbc implements InvocationHandler {
        private final Set<String> tables = new java.util.HashSet<>();
        private final List<Map<String, Object>> history = new ArrayList<>();
        private final List<String> executedMigrationStatements = new ArrayList<>();
        private int lockResult = 1;
        private int lockAcquireCount;
        private int lockReleaseCount;
        private String acquiredLockName;

        static MigrationJdbc coreSchema() {
            MigrationJdbc jdbc = new MigrationJdbc();
            jdbc.tables.addAll(Set.of("users", "rooms", "items", "emulator_settings"));
            return jdbc;
        }

        @SafeVarargs
        static MigrationJdbc withHistory(Map<String, Object>... rows) {
            MigrationJdbc jdbc = coreSchema();
            jdbc.tables.add("schema_migrations");
            jdbc.history.addAll(List.of(rows));
            return jdbc;
        }

        Connection connection() {
            return proxy(Connection.class, this);
        }

        List<Integer> historyVersions() {
            return history.stream().map(row -> (Integer) row.get("version")).toList();
        }

        boolean lockReleased() {
            return lockReleaseCount == lockAcquireCount && lockReleaseCount > 0;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getCatalog" -> "polaris_e2e_database_with_a_name_long_enough_to_require_a_bounded_lock";
                case "getMetaData" -> metadata();
                case "prepareStatement" -> prepared((String) args[0]);
                case "createStatement" -> statement();
                case "close" -> null;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private DatabaseMetaData metadata() {
            return proxy(DatabaseMetaData.class, (ignored, method, args) -> {
                if (method.getName().equals("getTables")) {
                    return resultSet(tables.stream()
                            .map(name -> Map.<String, Object>of("TABLE_NAME", name))
                            .toList());
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement prepared(String sql) {
            Map<Integer, Object> bindings = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, args) -> {
                if (method.getName().startsWith("set") && args != null && args.length >= 2) {
                    bindings.put((Integer) args[0], args[1]);
                    return null;
                }
                if (method.getName().equals("executeQuery")) {
                    if (sql.contains("GET_LOCK")) {
                        lockAcquireCount++;
                        acquiredLockName = String.valueOf(bindings.get(1));
                        assertTrue(acquiredLockName.length() <= 64);
                        return resultSet(List.of(Map.of("1", lockResult)));
                    }
                    if (sql.contains("RELEASE_LOCK")) {
                        assertEquals(acquiredLockName, bindings.get(1));
                        lockReleaseCount++;
                        return resultSet(List.of(Map.of("1", 1)));
                    }
                    return resultSet(history);
                }
                if (method.getName().equals("executeUpdate")) {
                    history.add(applied(
                            (Integer) bindings.get(1),
                            String.valueOf(bindings.get(2)),
                            String.valueOf(bindings.get(3)),
                            String.valueOf(bindings.get(4))));
                    return 1;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Statement statement() {
            return proxy(Statement.class, (ignored, method, args) -> {
                if ((method.getName().equals("executeUpdate") || method.getName().equals("execute"))
                        && args != null && args.length > 0) {
                    String sql = (String) args[0];
                    if (sql.contains("CREATE TABLE IF NOT EXISTS schema_migrations")) {
                        tables.add("schema_migrations");
                        return method.getReturnType() == boolean.class ? false : 0;
                    }
                    if (sql.contains("FAIL")) throw new SQLException("intentional failure");
                    executedMigrationStatements.add(sql);
                    return method.getReturnType() == boolean.class ? false : 0;
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (ignored, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getInt" -> ((Number) value(rows, index[0], args[0])).intValue();
            case "getLong" -> ((Number) value(rows, index[0], args[0])).longValue();
            case "getString" -> String.valueOf(value(rows, index[0], args[0]));
            case "getTimestamp" -> value(rows, index[0], args[0]);
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object value(List<Map<String, Object>> rows, int index, Object column) {
        if (column instanceof Integer) return rows.get(index).values().iterator().next();
        return rows.get(index).get(String.valueOf(column));
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
