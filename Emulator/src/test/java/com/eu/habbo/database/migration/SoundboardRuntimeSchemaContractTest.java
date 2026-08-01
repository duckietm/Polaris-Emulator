package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SoundboardRuntimeSchemaContractTest {

    @Test
    void packagedContractRequiresSoundboardVolumeAndAuditSchema() {
        JsonObject tables = JsonParser.parseString(RuntimeSchemaValidator.packagedContract())
                .getAsJsonObject()
                .getAsJsonObject("tables");

        assertTrue(tables.has("logs_soundboard"));

        JsonArray userSettingsColumns = tables.getAsJsonArray("users_settings");
        assertTrue(userSettingsColumns.asList().stream()
                .anyMatch(column -> column.getAsString().equals("volume_soundboard")));
    }
}
