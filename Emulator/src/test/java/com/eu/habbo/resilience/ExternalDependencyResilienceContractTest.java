package com.eu.habbo.resilience;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExternalDependencyResilienceContractTest {

    @Test
    void turnstileAndSmtpUseNamedFailClosedCircuitBreakers() throws Exception {
        String turnstile =
                compact(source("src/main/java/com/eu/habbo/networking/gameserver/auth/TurnstileVerifier.java"));
        String smtp = compact(source("src/main/java/com/eu/habbo/networking/gameserver/auth/SmtpMailService.java"));

        assertTrue(turnstile.contains("RuntimeResilienceRuntime.executeExternal(\"turnstile\""));
        assertTrue(turnstile.contains("()->false"));
        assertTrue(smtp.contains("RuntimeResilienceRuntime.executeExternal(\"smtp\""));
        assertTrue(smtp.contains("()->false"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
