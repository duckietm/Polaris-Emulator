package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
        // Guard the migration's CONTENT, not the checkout's line-ending mode.
        // The baseline is stored LF (see Gameserver/.gitattributes "eol=lf"), but a
        // Windows checkout with core.autocrlf can materialize it as CRLF, which would
        // otherwise change the raw-byte hash without any real content change. Normalize
        // CRLF/CR to LF before hashing so the immutability check is platform-independent
        // while still detecting any added, removed, or modified SQL. Read via ISO-8859-1
        // for a lossless byte<->char round-trip regardless of the file's actual encoding.
        String baselineText = new String(Files.readAllBytes(BASELINE), StandardCharsets.ISO_8859_1)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        byte[] baseline = baselineText.getBytes(StandardCharsets.ISO_8859_1);
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
