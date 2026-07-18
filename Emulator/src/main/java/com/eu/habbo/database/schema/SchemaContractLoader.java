package com.eu.habbo.database.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SchemaContractLoader {
    public static final String RESOURCE_NAME = "db/schema-contract.json";

    private SchemaContractLoader() {
    }

    public static SchemaContract load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new SchemaValidationException(
                        "Packaged database schema contract is missing: " + RESOURCE_NAME);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject tables = root.getAsJsonObject("tables");
            if (tables == null) {
                throw new SchemaValidationException("Database schema contract has no tables object");
            }

            Map<String, Set<String>> required = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> table : tables.entrySet()) {
                Set<String> columns = new LinkedHashSet<>();
                for (JsonElement column : table.getValue().getAsJsonArray()) {
                    columns.add(column.getAsString());
                }
                required.put(table.getKey(), columns);
            }
            return new SchemaContract(required);
        } catch (SchemaValidationException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new SchemaValidationException("Unable to load database schema contract", error);
        }
    }
}
