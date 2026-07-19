package com.eu.habbo.database.integrity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IntegrityContractLoader {
    public static final String RESOURCE = "db/integrity-contract.json";

    private IntegrityContractLoader() {
    }

    public static IntegrityContract load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing integrity contract: " + RESOURCE);
            return load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Could not read integrity contract", error);
        }
    }

    static IntegrityContract load(Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = required(root, "schemaVersion").getAsInt();
            List<RelationRequirement> relations = new ArrayList<>();
            for (JsonElement element : requiredArray(root, "logicalRelations")) {
                JsonObject relation = element.getAsJsonObject();
                relations.add(new RelationRequirement(
                        string(relation, "id"),
                        string(relation, "childTable"),
                        strings(relation, "childColumns"),
                        string(relation, "parentTable"),
                        strings(relation, "parentColumns"),
                        strings(relation, "ignoreZeroColumns"),
                        string(relation, "description"),
                        IntegrityCheckSource.LOGICAL_CONTRACT));
            }
            List<DuplicateRequirement> duplicates = new ArrayList<>();
            for (JsonElement element : requiredArray(root, "duplicateKeys")) {
                JsonObject duplicate = element.getAsJsonObject();
                duplicates.add(new DuplicateRequirement(
                        string(duplicate, "id"),
                        string(duplicate, "table"),
                        strings(duplicate, "columns"),
                        string(duplicate, "description")));
            }
            return new IntegrityContract(version, relations, duplicates);
        } catch (RuntimeException error) {
            if (error instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Invalid integrity contract", error);
        }
    }

    private static JsonElement required(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            throw new IllegalStateException("Integrity contract is missing " + name);
        }
        return value;
    }

    private static JsonArray requiredArray(JsonObject object, String name) {
        return required(object, name).getAsJsonArray();
    }

    private static String string(JsonObject object, String name) {
        return required(object, name).getAsString();
    }

    private static List<String> strings(JsonObject object, String name) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : requiredArray(object, name)) values.add(element.getAsString());
        return values;
    }
}
