package com.eu.habbo.networking.rconserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RCONServerHandlerContractTest {
    private static String serverSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java"));
    }

    private static String emulatorSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));
    }

    @Test
    void rconRequestsAreRateLimitedPerRemoteAddress() throws Exception {
        String source = serverSource();

        assertTrue(source.contains("LoadingCache<String, RateLimiter>"),
                "RCON server must keep per-remote-address rate limiters");
        assertTrue(source.contains("Caffeine.newBuilder()"),
                "RCON rate limiters must expire instead of growing forever");
        assertTrue(source.contains(".acquirePermission()"),
                "RCON handler must consume a Resilience4j permit before dispatching commands");
        assertTrue(source.contains("RATE_LIMITED"),
                "RCON callers need a deterministic response when the rate limit rejects a request");
    }

    @Test
    void rconRateLimitDefaultsAreRegisteredBeforeServerStarts() throws Exception {
        String source = emulatorSource();
        int registerIndex = source.indexOf("register(\"rcon.rate_limit.enabled\", \"1\")");
        int serverIndex = source.indexOf("new RCONServer");

        assertTrue(registerIndex >= 0, "RCON rate limiting must have a registered default toggle");
        assertTrue(source.contains("register(\"rcon.rate_limit.limit_for_period\", \"60\")"),
                "RCON rate limit must have a registered default limit");
        assertTrue(source.contains("register(\"rcon.rate_limit.refresh_period_ms\", \"1000\")"),
                "RCON rate limit must have a registered default refresh period");
        assertTrue(source.contains("register(\"rcon.rate_limit.timeout_ms\", \"0\")"),
                "RCON rate limit must reject immediately by default instead of blocking event loops");
        assertTrue(registerIndex < serverIndex, "RCON rate limit defaults must be registered before RCONServer is constructed");
    }
}
