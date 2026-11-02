package com.eu.habbo.habbohotel.wired.share;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Three bounds that were missing: a wired import carried up to 400 picks for a box that allows 20, a long run of
 * club purchases wrapped the subscription duration, and a plugin reload kept the old listener instances.
 */
class ImportAndReloadBoundsTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void anImportKeepsToTheBoxSelection() throws Exception {
        String importer = source("habbohotel/wired/share/WiredSetupImporter.java");

        assertTrue(importer.contains("int limit = WiredManager.MAXIMUM_FURNI_SELECTION;"));
        assertFalse(importer.contains("int limit = WiredInputGuard.maxFurniSelectionCount();"));
    }

    @Test
    void aSubscriptionDurationStaysInsideAnInt() throws Exception {
        assertTrue(source("habbohotel/users/subscriptions/Subscription.java")
                .contains("Math.min(Integer.MAX_VALUE, (long) this.duration + amount)"));
    }

    @Test
    void aPluginReloadDropsTheOldListeners() throws Exception {
        String plugins = source("plugin/PluginManager.java");

        assertTrue(plugins.indexOf("this.listenerReceivers.clear();")
                < plugins.indexOf("this.loadPluginsInternal();", plugins.indexOf("public void reload()")));
    }
}
