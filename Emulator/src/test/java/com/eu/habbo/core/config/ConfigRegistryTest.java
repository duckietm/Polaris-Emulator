package com.eu.habbo.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.core.ConfigurationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigRegistryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void registryCoversBothShippedStartupTemplates() throws Exception {
        Set<String> documented = ConfigRegistry.standard().names();
        Set<String> configured = new HashSet<>();
        configured.addAll(keys(Path.of("../config example/config.ini.example")));
        configured.addAll(keys(Path.of("../e2e/config.ini.template")));

        assertTrue(
                documented.containsAll(configured),
                () -> "Missing typed startup keys: "
                        + configured.stream()
                                .filter(key -> !documented.contains(key))
                                .sorted()
                                .toList());
    }

    @Test
    void malformedKnownValuesAreReportedAndUnknownPluginKeysRemainAllowed() throws Exception {
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "db.port=not-a-port\nplugin.example=value\n");
        ConfigurationManager configuration = new ConfigurationManager(config.toString());

        assertEquals(
                java.util.List.of("db.port"),
                ConfigRegistry.standard().validate(configuration).stream()
                        .map(ConfigRegistry.ValidationIssue::key)
                        .toList());
    }

    @Test
    void generatedReferenceMatchesTheCommittedDocument() throws Exception {
        assertEquals(
                Files.readString(Path.of("../docs/configuration-reference.md")),
                ConfigRegistry.standard().renderMarkdown());
    }

    @Test
    void networkWorkerAndBackpressureDefaultsAreTyped() {
        String reference = ConfigRegistry.standard().renderMarkdown();

        assertTrue(reference.contains("| `http.blocking.pool.size` | integer | `8` |"));
        assertTrue(reference.contains("| `io.netty.write_buffer.low_water_mark` | integer | `32768` |"));
        assertTrue(reference.contains("| `io.netty.write_buffer.high_water_mark` | integer | `65536` |"));
        assertTrue(reference.contains("| `io.netty.unwritable.timeout.seconds` | integer | `10` |"));
    }

    private static Set<String> keys(Path path) throws Exception {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            keys.add(trimmed.substring(0, trimmed.indexOf('=')).trim());
        }
        return keys;
    }
}
