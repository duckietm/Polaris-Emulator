package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** plugin.json was read with available() and a single read(), which may return only part of the file. */
class PluginJsonReadContractTest {

    @Test
    void theFileIsReadWholeWithALimit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/plugin/PluginManager.java"));

        assertFalse(source.contains("new byte[stream.available()]"));
        assertTrue(source.contains("stream.readNBytes(MAX_PLUGIN_JSON_BYTES + 1)"));
        assertTrue(PluginManager.MAX_PLUGIN_JSON_BYTES > 0);
    }
}
