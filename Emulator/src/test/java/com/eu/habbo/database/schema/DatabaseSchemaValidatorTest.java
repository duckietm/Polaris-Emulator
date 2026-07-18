package com.eu.habbo.database.schema;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaValidatorTest {
    @Test
    void acceptsRequiredSchemaAndIgnoresExtensions() {
        SchemaJdbc jdbc = new SchemaJdbc(Map.of(
                "users", Set.of("id", "username", "custom_column"),
                "rooms", Set.of("id"),
                "plugin_table", Set.of("id")));
        SchemaContract contract = new SchemaContract(Map.of(
                "users", Set.of("id", "username"),
                "rooms", Set.of("id")));

        SchemaValidationReport report =
                new DatabaseSchemaValidator(jdbc.connection(), contract).validate();

        assertEquals(2, report.requiredTables());
        assertEquals(3, report.requiredColumns());
    }

    @Test
    void reportsEveryMissingTableAndColumn() {
        SchemaJdbc jdbc = new SchemaJdbc(Map.of(
                "users", Set.of("id")));
        SchemaContract contract = new SchemaContract(Map.of(
                "users", Set.of("id", "username"),
                "rooms", Set.of("id")));

        SchemaValidationException error = assertThrows(
                SchemaValidationException.class,
                () -> new DatabaseSchemaValidator(jdbc.connection(), contract).validate());

        assertTrue(error.getMessage().contains("missing tables: rooms"));
        assertTrue(error.getMessage().contains("missing columns: users.username"));
    }

    private static final class SchemaJdbc implements InvocationHandler {
        private final Map<String, Set<String>> schema;

        private SchemaJdbc(Map<String, Set<String>> schema) {
            this.schema = new LinkedHashMap<>(schema);
        }

        Connection connection() {
            return proxy(Connection.class, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getCatalog" -> "polaris";
                case "getMetaData" -> metadata();
                case "close" -> null;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private DatabaseMetaData metadata() {
            return proxy(DatabaseMetaData.class, (ignored, method, args) -> {
                if (method.getName().equals("getTables")) {
                    return resultSet(schema.keySet().stream()
                            .map(name -> Map.<String, Object>of("TABLE_NAME", name))
                            .toList());
                }
                if (method.getName().equals("getColumns")) {
                    String table = String.valueOf(args[2]);
                    return resultSet(schema.getOrDefault(table, Set.of()).stream()
                            .map(name -> Map.<String, Object>of("COLUMN_NAME", name))
                            .toList());
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copy = new ArrayList<>(rows);
        int[] index = {-1};
        return proxy(ResultSet.class, (ignored, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < copy.size();
            case "getString" -> String.valueOf(copy.get(index[0]).get((String) args[0]));
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
