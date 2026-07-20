package com.eu.habbo;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleConfigurationTest {

    @Test
    void numericPoolValuesRemainParseable() throws Exception {
        Properties properties = loadExample();

        assertEquals(
                "20000",
                properties.getProperty(
                        "db.pool.leak_detection_ms"));
    }

    @Test
    void rememberTokenPlaceholderUsesChangeMePrefix()
            throws Exception {
        Properties properties = loadExample();

        assertEquals(
                "change-me-to-a-long-random-secret",
                properties.getProperty(
                        "login.remember.jwt.secret"));
    }

    private static Properties loadExample() throws Exception {
        Properties properties = new Properties();
        Path example = Path.of(
                "..", "config example", "config.ini.example");
        try (InputStream input = Files.newInputStream(example)) {
            properties.load(input);
        }
        return properties;
    }
}
