package com.eu.habbo.database.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowQueryLoggingConfigurationTest {

    @Test
    void writesSlowQueriesToTheirOwnRotatingFile() throws Exception {
        String logback = Files.readString(Path.of("src/main/resources/logback.xml"));

        assertTrue(logback.contains("name=\"SlowQueries\""));
        assertTrue(logback.contains("logging/database/slow-queries.log"));
        assertTrue(logback.contains("slow-queries.%d{yyyy-MM-dd}.%i.gz"));
        assertTrue(logback.contains("name=\"database.slow_query\""));
        assertTrue(logback.contains("<appender-ref ref=\"SlowQueries\""));
    }
}
