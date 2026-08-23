package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class CatalogMigrationImmutabilityContractTest {
    private static final Path BASELINE = Path.of("src/main/resources/db/migration/V20260518000000__base_database.sql");
    private static final Path OCTANE_SETTINGS =
            Path.of("src/main/resources/db/migration/V20260823183000__align_runtime_settings_with_octane.sql");

    @Test
    void keepsAppliedBaselineImmutableAndMovesRuntimeRenamesForward() throws Exception {
        byte[] baseline = Files.readAllBytes(BASELINE);
        String hash = HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(baseline));
        String migration = Files.readString(OCTANE_SETTINGS);

        assertEquals("2ADF4D312DCB54FE0F96B4D455C1C57260503F5FDB2DC7A1F95FDBEBA8FD5618", hash);
        assertTrue(migration.contains("/var/www/Octane-UI/dist/configuration/renderer-config.json"));
        assertTrue(migration.contains("REPLACE(`comment`, 'NitroV3 Login', 'OctaneUI Login')"));
        assertTrue(migration.contains("WHERE `key` = 'furni.editor.renderer.config.path'"));
    }
}
