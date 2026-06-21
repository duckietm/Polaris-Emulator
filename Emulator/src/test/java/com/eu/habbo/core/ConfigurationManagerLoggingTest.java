package com.eu.habbo.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.eu.habbo.Emulator;
import com.eu.habbo.EmulatorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the fix that stops {@link ConfigurationManager} from logging an ERROR
 * every time an optional config key (one with a caller-supplied default) is
 * absent. The lookup must still return the default; it just must not pollute the
 * ERROR stream. Captures log events with a logback ListAppender.
 */
class ConfigurationManagerLoggingTest {

    private ConfigurationManager previous;
    private Logger cmLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setup() throws Exception {
        previous = Emulator.getConfig();
        EmulatorTestSupport.installConfig(Map.of("present.key", "5"));

        cmLogger = (Logger) LoggerFactory.getLogger(ConfigurationManager.class);
        appender = new ListAppender<>();
        appender.start();
        cmLogger.addAppender(appender); // attach AFTER construction so its INFO line isn't captured
    }

    @AfterEach
    void teardown() throws Exception {
        if (cmLogger != null && appender != null) {
            cmLogger.detachAppender(appender);
        }
        EmulatorTestSupport.setConfig(previous);
    }

    @Test
    void absentOptionalKeyReturnsDefaultWithoutLoggingError() {
        int value = Emulator.getConfig().getInt("totally.absent.key", 99);
        assertEquals(99, value); // default still honoured

        boolean anyError = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.ERROR);
        assertFalse(anyError, "an absent optional config key must not log at ERROR level");
    }

    @Test
    void presentKeyStillResolves() {
        assertEquals(5, Emulator.getConfig().getInt("present.key", 0));
    }
}
