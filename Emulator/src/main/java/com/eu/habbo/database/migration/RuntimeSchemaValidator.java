package com.eu.habbo.database.migration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies every required table and column in the packaged runtime contract.
 *
 * <p>The contract is generated from a fully migrated Flyway database. Additional
 * plugin tables and custom columns are compatible, while missing contract
 * objects fail startup. Security-sensitive password-reset columns also retain
 * explicit type, length, nullability, and unique-key checks.
 */
final class RuntimeSchemaValidator {

    static final String CONTRACT_RESOURCE = "db/runtime-schema-contract.json";

    private static final Gson GSON = new Gson();
    private static final Set<String> INTEGER_TYPES =
            Set.of("tinyint", "smallint", "mediumint", "int", "bigint");

    private static final List<TableRequirement> REQUIREMENTS = loadRequirements();

    private static List<TableRequirement> loadRequirements() {
        List<TableRequirement> requirements = new ArrayList<>();
        try (InputStream input = RuntimeSchemaValidator.class.getClassLoader()
                .getResourceAsStream(CONTRACT_RESOURCE)) {
            if (input == null) {
                throw new MigrationException(
                        "Packaged runtime schema contract is missing: " + CONTRACT_RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 1) {
                throw new MigrationException("Unsupported runtime schema contract version");
            }
            JsonObject tables = root.getAsJsonObject("tables");
            if (tables == null || tables.isEmpty()) {
                throw new MigrationException("Runtime schema contract contains no tables");
            }
            for (Map.Entry<String, JsonElement> table : tables.entrySet()) {
                List<ColumnRequirement> columns = new ArrayList<>();
                for (JsonElement column : table.getValue().getAsJsonArray()) {
                    columns.add(columnRequirement(table.getKey(), column.getAsString()));
                }
                List<List<String>> uniqueKeys = table.getKey().equals("password_resets")
                        ? List.of(List.of("user_id"), List.of("token"))
                        : List.of();
                requirements.add(new TableRequirement(
                        normalize(table.getKey()),
                        List.copyOf(columns),
                        uniqueKeys));
            }
            return List.copyOf(requirements);
        } catch (MigrationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new MigrationException("Could not load the runtime schema contract", e);
        }
    }

    private RuntimeSchemaValidator() {
    }

    static void validate(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");

        try (Connection connection = dataSource.getConnection()) {
            SchemaSnapshot schema = SchemaSnapshot.load(connection);
            List<String> violations = new ArrayList<>();

            for (TableRequirement table : REQUIREMENTS) {
                Map<String, ColumnDefinition> actualColumns = schema.columns().get(table.name());
                if (actualColumns == null) {
                    violations.add("missing table " + table.name());
                    continue;
                }

                for (ColumnRequirement column : table.columns()) {
                    ColumnDefinition actual = actualColumns.get(column.name());
                    if (actual == null) {
                        violations.add("missing column " + table.name() + "." + column.name());
                    } else {
                        column.validate(table.name(), actual, violations);
                    }
                }

                Set<List<String>> actualUniqueKeys =
                        schema.uniqueKeys().getOrDefault(table.name(), Set.of());
                for (List<String> uniqueKey : table.uniqueKeys()) {
                    if (!actualUniqueKeys.contains(uniqueKey)) {
                        violations.add("missing unique key " + table.name()
                                + "(" + String.join(", ", uniqueKey) + ")");
                    }
                }
            }

            if (!violations.isEmpty()) {
                throw new MigrationException(
                        "Runtime database schema is incompatible: " + String.join("; ", violations));
            }
        } catch (MigrationException e) {
            throw e;
        } catch (SQLException e) {
            throw new MigrationException("Could not validate the runtime database schema", e);
        }
    }

    static String generateContract(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            SchemaSnapshot schema = SchemaSnapshot.load(connection);
            List<String> tables = schema.columns().keySet().stream()
                    .filter(table -> !table.equals("flyway_schema_history"))
                    .sorted()
                    .toList();
            StringBuilder json = new StringBuilder("""
                    {
                      "schemaVersion": 1,
                      "source": "Flyway migrated schema",
                      "tables": {
                    """);
            for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                String table = tables.get(tableIndex);
                List<String> columns = schema.columns().get(table).keySet().stream()
                        .filter(column -> !(table.equals("pet_actions") && column.equals("id")))
                        .sorted()
                        .toList();
                json.append("    ").append(GSON.toJson(table)).append(": [");
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    if (columnIndex > 0) json.append(", ");
                    json.append(GSON.toJson(columns.get(columnIndex)));
                }
                json.append(']');
                if (tableIndex < tables.size() - 1) json.append(',');
                json.append('\n');
            }
            return json.append("  }\n}\n").toString();
        } catch (SQLException e) {
            throw new MigrationException("Could not generate the runtime database schema contract", e);
        }
    }

    static String packagedContract() {
        try (InputStream input = RuntimeSchemaValidator.class.getClassLoader()
                .getResourceAsStream(CONTRACT_RESOURCE)) {
            if (input == null) {
                throw new MigrationException(
                        "Packaged runtime schema contract is missing: " + CONTRACT_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MigrationException("Could not read the runtime schema contract", e);
        }
    }

    private static ColumnRequirement columnRequirement(String table, String column) {
        if (!table.equals("password_resets")) {
            return new ColumnRequirement(normalize(column), Set.of(), null, null);
        }
        return switch (column) {
            case "user_id" -> new ColumnRequirement(column, INTEGER_TYPES, null, null);
            case "token" -> new ColumnRequirement(column, Set.of("varchar"), 64L, false);
            case "expires_at" ->
                    new ColumnRequirement(column, Set.of("timestamp", "datetime"), null, false);
            case "created_ip" ->
                    new ColumnRequirement(column, Set.of("varchar"), 45L, false);
            default -> new ColumnRequirement(normalize(column), Set.of(), null, null);
        };
    }

    private record TableRequirement(
            String name,
            List<ColumnRequirement> columns,
            List<List<String>> uniqueKeys) {
    }

    private record ColumnRequirement(
            String name,
            Set<String> allowedTypes,
            Long minimumLength,
            Boolean nullable) {

        private void validate(
                String table,
                ColumnDefinition actual,
                List<String> violations) {
            String qualifiedName = table + "." + name;
            if (!allowedTypes.isEmpty() && !allowedTypes.contains(actual.dataType())) {
                violations.add(qualifiedName + " has type " + actual.dataType()
                        + "; expected one of " + allowedTypes);
            }
            if (minimumLength != null
                    && (actual.characterLength() == null
                    || actual.characterLength() < minimumLength)) {
                violations.add(qualifiedName + " has length " + actual.characterLength()
                        + "; expected at least " + minimumLength);
            }
            if (nullable != null && actual.nullable() != nullable) {
                violations.add(qualifiedName + " nullable=" + actual.nullable()
                        + "; expected nullable=" + nullable);
            }
        }
    }

    private record ColumnDefinition(
            String dataType,
            Long characterLength,
            boolean nullable) {
    }

    private record SchemaSnapshot(
            Map<String, Map<String, ColumnDefinition>> columns,
            Map<String, Set<List<String>>> uniqueKeys) {

        private static SchemaSnapshot load(Connection connection) throws SQLException {
            Map<String, Map<String, ColumnDefinition>> columns = new LinkedHashMap<>();
            try (var statement = connection.prepareStatement("""
                    SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE,
                           CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String table = normalize(rows.getString("TABLE_NAME"));
                    String column = normalize(rows.getString("COLUMN_NAME"));
                    long length = rows.getLong("CHARACTER_MAXIMUM_LENGTH");
                    Long characterLength = rows.wasNull() ? null : length;
                    columns.computeIfAbsent(table, ignored -> new LinkedHashMap<>())
                            .put(column, new ColumnDefinition(
                                    normalize(rows.getString("DATA_TYPE")),
                                    characterLength,
                                    "YES".equalsIgnoreCase(rows.getString("IS_NULLABLE"))));
                }
            }

            Map<String, Map<String, List<IndexedColumn>>> indexes = new HashMap<>();
            try (var statement = connection.prepareStatement("""
                    SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                    ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                    """);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getInt("NON_UNIQUE") != 0) {
                        continue;
                    }
                    String table = normalize(rows.getString("TABLE_NAME"));
                    String index = normalize(rows.getString("INDEX_NAME"));
                    String column = rows.getString("COLUMN_NAME");
                    if (column == null) {
                        continue;
                    }
                    indexes.computeIfAbsent(table, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(index, ignored -> new ArrayList<>())
                            .add(new IndexedColumn(
                                    rows.getInt("SEQ_IN_INDEX"),
                                    normalize(column)));
                }
            }

            Map<String, Set<List<String>>> uniqueKeys = new LinkedHashMap<>();
            indexes.forEach((table, tableIndexes) -> {
                Set<List<String>> keys = new LinkedHashSet<>();
                tableIndexes.values().forEach(indexColumns -> keys.add(indexColumns.stream()
                        .sorted(java.util.Comparator.comparingInt(IndexedColumn::position))
                        .map(IndexedColumn::name)
                        .toList()));
                uniqueKeys.put(table, Set.copyOf(keys));
            });

            Map<String, Map<String, ColumnDefinition>> immutableColumns = new LinkedHashMap<>();
            columns.forEach((table, tableColumns) ->
                    immutableColumns.put(table, Map.copyOf(tableColumns)));
            return new SchemaSnapshot(Map.copyOf(immutableColumns), Map.copyOf(uniqueKeys));
        }
    }

    private record IndexedColumn(int position, String name) {
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
