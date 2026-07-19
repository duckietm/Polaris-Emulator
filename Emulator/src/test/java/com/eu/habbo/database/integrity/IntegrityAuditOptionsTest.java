package com.eu.habbo.database.integrity;

import com.eu.habbo.core.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityAuditOptionsTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesExplicitModesAndRejectsNearMisses() {
        assertTrue(IntegrityAuditOptions.parse(new String[0]).modeOverride().isEmpty());
        assertEquals(
                IntegrityAuditMode.STRICT,
                IntegrityAuditOptions.parse(new String[]{"--integrity=strict"})
                        .modeOverride().orElseThrow());
        assertEquals(
                IntegrityAuditMode.OFF,
                IntegrityAuditOptions.parse(new String[]{"--integrity=off"})
                        .modeOverride().orElseThrow());

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityAuditOptions.parse(new String[]{"--integrity=repair"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityAuditOptions.parse(new String[]{"--integrity-strict"}));
    }

    @Test
    void cliModeOverridesBoundedConfiguration() throws Exception {
        Path configFile = tempDir.resolve("config.ini");
        Files.writeString(configFile, """
                db.integrity.audit.mode=warn
                db.integrity.audit.sample_limit=7
                db.integrity.audit.query_timeout_seconds=11
                db.integrity.audit.max_duration_seconds=42
                """);
        ConfigurationManager config = new ConfigurationManager(configFile.toString());

        IntegrityAuditSettings settings = IntegrityAuditSettings.resolve(
                config,
                IntegrityAuditOptions.parse(new String[]{"--integrity=strict"}));

        assertEquals(IntegrityAuditMode.STRICT, settings.mode());
        assertEquals(7, settings.sampleLimit());
        assertEquals(11, settings.queryTimeoutSeconds());
        assertEquals(42, settings.maxDurationSeconds());
    }

    @Test
    void rejectsUnboundedAuditSettings() throws Exception {
        Path configFile = tempDir.resolve("config.ini");
        Files.writeString(configFile, """
                db.integrity.audit.mode=warn
                db.integrity.audit.sample_limit=1000
                db.integrity.audit.query_timeout_seconds=30
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrityAuditSettings.resolve(
                        new ConfigurationManager(configFile.toString()),
                        IntegrityAuditOptions.parse(new String[0])));
    }
}
