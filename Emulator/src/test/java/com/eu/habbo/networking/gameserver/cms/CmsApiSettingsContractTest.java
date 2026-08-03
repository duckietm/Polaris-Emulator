package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the {@code emulator_api} table, its seed migration, the runtime schema
 * contract and {@code config.ini.example} consistent. These are file assertions —
 * no database required — guarding that the settings genuinely moved out of
 * config.ini and into the table without one artifact drifting from the others.
 */
class CmsApiSettingsContractTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20260803120000__cms_api_settings.sql");
    private static final Path SCHEMA_CONTRACT = Path.of("src/main/resources/db/runtime-schema-contract.json");
    private static final Path CONFIG_EXAMPLE = Path.of("../config example/config.ini.example");

    // The settings that moved from config.ini into the emulator_api table.
    private static final List<String> RELOCATED_KEYS = List.of(
            "cms.api.keys",
            "cms.api.timestamp.skew.seconds",
            "cms.api.nonce.ttl.seconds",
            "cms.api.max_payload_bytes",
            "cms.api.require_tls",
            "cms.api.rate_limit.enabled",
            "cms.api.rate_limit.limit_for_period",
            "cms.api.rate_limit.refresh_period_ms");

    @Test
    void migrationCreatesAndSeedsTheTable() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(
                sql.contains("CREATE TABLE IF NOT EXISTS `emulator_api`"),
                "Migration must create the emulator_api table");
        for (String key : RELOCATED_KEYS) {
            assertTrue(sql.contains("'" + key + "'"), "Migration must seed a row for " + key);
        }
        // Bootstrap/network-gate settings stay in config.ini, not seeded as rows
        // (they may still be named in an explanatory comment, hence the quotes).
        assertFalse(sql.contains("'cms.api.enabled'"), "cms.api.enabled must not be seeded into the table");
        assertFalse(sql.contains("'cms.api.allowed'"), "cms.api.allowed must not be seeded into the table");
    }

    @Test
    void schemaContractDeclaresTheTable() throws Exception {
        JsonObject contract =
                JsonParser.parseString(Files.readString(SCHEMA_CONTRACT)).getAsJsonObject();
        JsonObject tables = contract.getAsJsonObject("tables");
        assertTrue(tables.has("emulator_api"), "runtime-schema-contract.json must declare emulator_api");
        JsonArray columns = tables.getAsJsonArray("emulator_api");
        assertEquals(3, columns.size());
        assertEquals("comment", columns.get(0).getAsString());
        assertEquals("key", columns.get(1).getAsString());
        assertEquals("value", columns.get(2).getAsString());
    }

    @Test
    void configExampleKeepsBootstrapKeysButNotTheRelocatedSettings() throws Exception {
        String config = Files.readString(CONFIG_EXAMPLE);
        assertTrue(config.contains("cms.api.enabled="), "config.ini must keep cms.api.enabled");
        assertTrue(config.contains("cms.api.allowed="), "config.ini must keep cms.api.allowed");
        // Relocated tuning keys must no longer be defined in config.ini as settings.
        assertFalse(
                config.contains("cms.api.timestamp.skew.seconds="), "Relocated setting must not remain in config.ini");
        assertFalse(
                config.contains("cms.api.rate_limit.refresh_period_ms="),
                "Relocated setting must not remain in config.ini");
    }
}
