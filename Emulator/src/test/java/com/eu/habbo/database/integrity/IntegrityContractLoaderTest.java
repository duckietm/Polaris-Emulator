package com.eu.habbo.database.integrity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityContractLoaderTest {
    @Test
    void packagedContractCoversCoreOperationalRelationships() {
        IntegrityContract contract = IntegrityContractLoader.load(getClass().getClassLoader());

        assertTrue(contract.logicalRelations().size() >= 35);
        assertTrue(contract.duplicateKeys().size() >= 10);
        assertTrue(contract.logicalRelations().stream()
                .anyMatch(check -> check.id().equals("items.base-item")));
        assertTrue(contract.logicalRelations().stream()
                .anyMatch(check -> check.id().equals("messenger.friend-category-owner")));
    }

    @Test
    void everyContractIdentifierExistsInTheRuntimeSchema() {
        IntegrityContract contract = IntegrityContractLoader.load(getClass().getClassLoader());
        JsonObject tables = JsonParser.parseReader(new InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream(
                                "db/runtime-schema-contract.json"),
                        StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("tables");

        contract.logicalRelations().forEach(check -> {
            assertColumns(tables, check.childTable(), check.childColumns());
            assertColumns(tables, check.parentTable(), check.parentColumns());
        });
        contract.duplicateKeys().forEach(check ->
                assertColumns(tables, check.table(), check.columns()));
    }

    @Test
    void rejectsSqlIdentifiersAndDuplicateCheckIds() {
        String invalidIdentifier = """
                {
                  "schemaVersion": 1,
                  "logicalRelations": [{
                    "id": "bad",
                    "childTable": "users; DROP TABLE users",
                    "childColumns": ["id"],
                    "parentTable": "users",
                    "parentColumns": ["id"],
                    "ignoreZeroColumns": [],
                    "description": "bad"
                  }],
                  "duplicateKeys": []
                }
                """;
        assertThrows(
                IllegalStateException.class,
                () -> IntegrityContractLoader.load(new StringReader(invalidIdentifier)));

        String duplicateIds = """
                {
                  "schemaVersion": 1,
                  "logicalRelations": [],
                  "duplicateKeys": [
                    {"id":"same","table":"users","columns":["id"],"description":"a"},
                    {"id":"same","table":"rooms","columns":["id"],"description":"b"}
                  ]
                }
                """;
        assertThrows(
                IllegalStateException.class,
                () -> IntegrityContractLoader.load(new StringReader(duplicateIds)));
    }

    private static void assertColumns(JsonObject tables, String table, Iterable<String> columns) {
        assertTrue(tables.has(table), () -> "Missing runtime table " + table);
        Set<String> actual = new HashSet<>();
        tables.getAsJsonArray(table).forEach(column -> actual.add(column.getAsString()));
        for (String column : columns) {
            assertTrue(actual.contains(column), () -> "Missing runtime column " + table + "." + column);
        }
    }
}
