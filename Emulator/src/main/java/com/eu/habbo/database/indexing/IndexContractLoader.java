package com.eu.habbo.database.indexing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IndexContractLoader {
    public static final String RESOURCE = "db/index-contract.json";

    private IndexContractLoader() {
    }

    public static IndexContract load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing index contract: " + RESOURCE);
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 1) {
                throw new IllegalStateException("Unsupported index contract version");
            }
            JsonArray indexes = root.getAsJsonArray("indexes");
            if (indexes == null || indexes.isEmpty()) {
                throw new IllegalStateException("Index contract contains no requirements");
            }

            List<IndexRequirement> requirements = new ArrayList<>();
            for (JsonElement element : indexes) {
                JsonObject index = element.getAsJsonObject();
                List<String> columns = new ArrayList<>();
                for (JsonElement column : index.getAsJsonArray("columns")) {
                    columns.add(column.getAsString());
                }
                requirements.add(new IndexRequirement(
                        index.get("name").getAsString(),
                        index.get("table").getAsString(),
                        columns,
                        index.get("purpose").getAsString()));
            }
            return new IndexContract(requirements);
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Could not load index contract", error);
        }
    }
}
