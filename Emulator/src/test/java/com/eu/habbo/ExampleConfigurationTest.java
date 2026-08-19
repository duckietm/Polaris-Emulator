package com.eu.habbo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ExampleConfigurationTest {

    @Test
    void numericPoolValuesRemainParseable() throws Exception {
        Properties properties = loadExample();

        assertEquals("20000", properties.getProperty("db.pool.leak_detection_ms"));
    }

    @Test
    void rememberTokenPlaceholderUsesChangeMePrefix() throws Exception {
        Properties properties = loadExample();

        assertEquals("change-me-to-a-long-random-secret", properties.getProperty("login.remember.jwt.secret"));
    }

    @Test
    void operationalProfilesRemainStartupFileOwned() throws Exception {
        Properties properties = loadExample();

        assertEquals("custom", properties.getProperty("runtime.operational.profile"));
        assertEquals("0", properties.getProperty("persistence.executor.threads"));
    }

    private static Properties loadExample() throws Exception {
        Properties properties = new Properties();
        Path example = Path.of("..", "config example", "config.ini.example");
        try (InputStream input = Files.newInputStream(example)) {
            properties.load(input);
        }
        return properties;
    }
}
