package com.eu.habbo.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationManagerDefaultsTest {
    @TempDir
    Path tempDir;

    @Test
    void missingBooleanUsesTheRequestedDefault() throws Exception {
        Path config = tempDir.resolve("config.ini");
        Files.writeString(config, "");
        ConfigurationManager manager = new ConfigurationManager(config.toString());

        assertTrue(manager.getBoolean("missing.true", true));
        assertFalse(manager.getBoolean("missing.false", false));
    }

    @Test
    void explicitDefaultsDoNotProduceMissingKeyErrors() throws Exception {
        Path config = tempDir.resolve("config.ini");
        Files.writeString(config, "");
        ConfigurationManager manager = new ConfigurationManager(config.toString());
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigurationManager.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertEquals("fallback", manager.getValue("missing.value", "fallback"));
            assertTrue(manager.getBoolean("missing.boolean", true));
            assertEquals(7, manager.getInt("missing.integer", 7));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }

        assertTrue(events.list.stream().noneMatch(event ->
                event.getFormattedMessage().startsWith("Config key not found")));
    }
}
