package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void managementMigrationWritesEnumPermissionValuesAsStrings() throws Exception {
        String migration =
                Files.readString(Path.of("src/main/resources/db/migration/V20260801090000__soundboard_management.sql"));

        assertTrue(migration.contains("THEN IF(src.`', `column_name`, '` > 0, ''1'', ''0'')"));
        assertTrue(migration.contains("ELSE ''0'' END"));
    }
}
