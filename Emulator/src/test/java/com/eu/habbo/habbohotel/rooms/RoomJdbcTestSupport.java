package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

final class RoomJdbcTestSupport {

    private RoomJdbcTestSupport() {
    }

    static InstalledDatabase install(RecordingDataSource dataSource) throws Exception {
        Field field = Emulator.class.getDeclaredField("database");
        field.setAccessible(true);
        Database original = (Database) field.get(null);
        field.set(null, databaseUsing(dataSource));
        return new InstalledDatabase(field, original);
    }

    private static Database databaseUsing(HikariDataSource dataSource) throws Exception {
        Constructor<Database> constructor =
                Database.class.getDeclaredConstructor(HikariDataSource.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource);
    }

    record SqlCall(
            String sql,
            Map<Integer, Object> parameters,
            String operation) {
    }

    static final class RecordingDataSource extends HikariDataSource {
        private final List<SqlCall> calls = new CopyOnWriteArrayList<>();
        private Function<String, List<Map<String, Object>>> rows =
                ignored -> List.of();
        private volatile boolean failQueries;
        private volatile boolean failUpdates;

        List<SqlCall> calls() {
            return List.copyOf(this.calls);
        }

        void rows(Function<String, List<Map<String, Object>>> rows) {
            this.rows = rows;
        }

        void failUpdates(boolean failUpdates) {
            this.failUpdates = failUpdates;
        }

        void failQueries(boolean failQueries) {
            this.failQueries = failQueries;
        }

        @Override
        public Connection getConnection() {
            return proxy(Connection.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) arguments[0]);
                case "close" -> null;
                case "isClosed" -> false;
                case "toString" -> "room JDBC fixture connection";
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> {
                String name = method.getName();
                if (name.startsWith("set") && arguments != null && arguments.length >= 2) {
                    parameters.put((Integer) arguments[0], arguments[1]);
                    return null;
                }

                return switch (name) {
                    case "executeQuery" -> {
                        this.calls.add(new SqlCall(
                                sql,
                                Map.copyOf(parameters),
                                "query"));
                        if (this.failQueries) {
                            throw new SQLException("fixture query failure");
                        }
                        yield resultSet(this.rows.apply(sql));
                    }
                    case "executeUpdate" -> {
                        this.calls.add(new SqlCall(
                                sql,
                                Map.copyOf(parameters),
                                "update"));
                        if (this.failUpdates) {
                            throw new SQLException("fixture update failure");
                        }
                        yield 1;
                    }
                    case "execute" -> {
                        this.calls.add(new SqlCall(
                                sql,
                                Map.copyOf(parameters),
                                "execute"));
                        if (this.failUpdates) {
                            throw new SQLException("fixture update failure");
                        }
                        yield false;
                    }
                    case "close" -> null;
                    case "toString" -> "room JDBC fixture statement";
                    default -> defaultValue(method.getReturnType());
                };
            });
        }
    }

    static final class InstalledDatabase implements AutoCloseable {
        private final Field field;
        private final Database original;

        private InstalledDatabase(Field field, Database original) {
            this.field = field;
            this.original = original;
        }

        @Override
        public void close() throws IllegalAccessException {
            this.field.set(null, this.original);
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copiedRows = rows.stream()
                .map(HashMap::new)
                .map(Map::copyOf)
                .toList();
        int[] index = {-1};
        boolean[] wasNull = {false};

        return proxy(ResultSet.class, (ignored, method, arguments) -> {
            String name = method.getName();
            if ("next".equals(name)) {
                index[0]++;
                return index[0] < copiedRows.size();
            }
            if ("close".equals(name)) {
                return null;
            }
            if ("wasNull".equals(name)) {
                return wasNull[0];
            }
            if ("toString".equals(name)) {
                return "room JDBC fixture result set";
            }

            Object key = arguments[0];
            Object value = value(copiedRows.get(index[0]), key);
            wasNull[0] = value == null;
            return switch (name) {
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getShort" -> value == null ? (short) 0 : ((Number) value).shortValue();
                case "getDouble" -> value == null ? 0D : ((Number) value).doubleValue();
                case "getBoolean" -> value instanceof Boolean flag
                        ? flag
                        : value != null && ((Number) value).intValue() != 0;
                case "getString" -> value == null ? null : String.valueOf(value);
                default -> defaultValue(method.getReturnType());
            };
        });
    }

    private static Object value(Map<String, Object> row, Object key) {
        if (key instanceof String column) {
            return row.get(column);
        }
        if (key instanceof Integer position) {
            return row.values().stream().skip(position - 1L).findFirst().orElse(null);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
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
