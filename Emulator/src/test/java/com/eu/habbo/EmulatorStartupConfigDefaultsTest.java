package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmulatorStartupConfigDefaultsTest {

    @Test
    void registersStartupConfigDefaultsBeforePluginConfigEvent() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/PolarisBootstrap.java"));

        int defaults = source.indexOf("registerConfigurationDefaults.run()");
        int plugins = source.indexOf("new PluginManager(");

        assertTrue(defaults > 0, "bootstrap must register startup config defaults explicitly");
        assertTrue(plugins > 0, "bootstrap must initialize the plugin manager explicitly");
        assertTrue(defaults < plugins, "startup config defaults must exist before plugin reload/config events");
    }

    @Test
    void guiDefaultsArePartOfStartupDefaults() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));

        int defaultsMethod = source.indexOf("private static void registerStartupConfigDefaults()");
        int guiEnabled = source.indexOf("Emulator.config.register(\"gui.enabled\", \"0\")");
        int guiAutostart = source.indexOf("Emulator.config.register(\"gui.autostart.enabled\", \"0\")");

        assertTrue(defaultsMethod > 0, "startup defaults helper must exist");
        assertTrue(guiEnabled > defaultsMethod, "gui.enabled must be registered by startup defaults");
        assertTrue(guiAutostart > defaultsMethod, "gui.autostart.enabled must be registered by startup defaults");
    }

    @Test
    void bootstrapDoesNotOverrideOperatorDatabasePoolSizing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/PolarisBootstrap.java"));

        assertFalse(source.contains("database.getDataSource().setMaximumPoolSize"));
        assertFalse(source.contains("database.getDataSource().setMinimumIdle"));
    }
}
