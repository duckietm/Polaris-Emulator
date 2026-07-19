package com.eu.habbo.database.indexing;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIndexAuditorTest {
    @Test
    void acceptsEquivalentCustomIndexesAndOrderedLeftPrefixCoverage() {
        IndexContract contract = new IndexContract(List.of(
                requirement("badges", "users_badges", "user_id", "badge_code"),
                requirement("pets", "users_pets", "room_id")));
        IndexJdbc jdbc = new IndexJdbc(Map.of(
                "users_badges", List.of(index(
                        "custom_badge_lookup", false, "USER_ID", "BADGE_CODE", "SLOT_ID")),
                "users_pets", List.of(index("custom_pet_owner", false, "user_id", "room_id"))));

        IndexAuditReport report = new DatabaseIndexAuditor(jdbc.connection(), contract).audit();

        assertEquals(1, report.coveredRequirements());
        assertEquals(List.of("users_pets.pets(room_id)"), report.missingRequirements());
    }

    @Test
    void reportsOnlyNonUniqueLeftPrefixIndexesAsRedundantCandidates() {
        IndexContract contract = new IndexContract(List.of(
                requirement("friends", "messenger_friendships", "user_one_id")));
        IndexJdbc jdbc = new IndexJdbc(Map.of(
                "messenger_friendships", List.of(
                        index("idx_short", false, "user_one_id"),
                        index("idx_long", false, "user_one_id", "user_two_id"),
                        index("uq_friend", true, "user_one_id", "user_two_id"))));

        IndexAuditReport report = new DatabaseIndexAuditor(jdbc.connection(), contract).audit();

        assertEquals(1, report.coveredRequirements());
        assertEquals(List.of(
                "messenger_friendships.idx_short is covered by idx_long"),
                report.redundantCandidates());
    }

    private static IndexRequirement requirement(String name, String table, String... columns) {
        return new IndexRequirement(name, table, List.of(columns), "test purpose");
    }

    private static IndexDefinition index(String name, boolean unique, String... columns) {
        return new IndexDefinition(name, unique, List.of(columns));
    }

    private record IndexDefinition(String name, boolean unique, List<String> columns) {
    }

    private static final class IndexJdbc implements InvocationHandler {
        private final Map<String, List<IndexDefinition>> indexes;

        private IndexJdbc(Map<String, List<IndexDefinition>> indexes) {
            this.indexes = new LinkedHashMap<>(indexes);
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
                if (!method.getName().equals("getIndexInfo")) {
                    return defaultValue(method.getReturnType());
                }
                String table = String.valueOf(args[2]).toLowerCase();
                List<Map<String, Object>> rows = new ArrayList<>();
                for (IndexDefinition index : indexes.getOrDefault(table, List.of())) {
                    for (int position = 0; position < index.columns().size(); position++) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("INDEX_NAME", index.name());
                        row.put("COLUMN_NAME", index.columns().get(position));
                        row.put("ORDINAL_POSITION", (short) (position + 1));
                        row.put("NON_UNIQUE", !index.unique());
                        rows.add(row);
                    }
                }
                return resultSet(rows);
            });
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (ignored, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getString" -> String.valueOf(rows.get(index[0]).get((String) args[0]));
            case "getShort" -> ((Number) rows.get(index[0]).get((String) args[0])).shortValue();
            case "getBoolean" -> rows.get(index[0]).get((String) args[0]);
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
