package com.eu.habbo.networking.rconserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RCONServerHandlerContractTest {
    private static String serverSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java"));
    }

    private static String handlerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServerHandler.java"));
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

    @Test
    void rconPayloadSizeIsBoundedBeforeBufferCopy() throws Exception {
        String handler = handlerSource();
        String emulator = emulatorSource();

        int readableBytes = handler.indexOf("int readableBytes = data.readableBytes()");
        int maxPayload = handler.indexOf("int maxPayloadBytes = maxPayloadBytes()", readableBytes);
        int oversizedGuard = handler.indexOf("readableBytes > maxPayloadBytes", maxPayload);
        int byteArray = handler.indexOf("new byte[readableBytes]", readableBytes);

        assertTrue(handler.contains("DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024"),
                "RCON handler should have a conservative default payload cap");
        assertTrue(handler.contains("rcon.max_payload_bytes"),
                "RCON max payload should be configurable");
        assertTrue(readableBytes > -1, "RCON handler must read ByteBuf size before allocation");
        assertTrue(maxPayload > readableBytes, "RCON handler must resolve max payload before allocation");
        assertTrue(oversizedGuard > maxPayload, "RCON handler must reject oversized payloads");
        assertTrue(oversizedGuard < byteArray, "Oversized RCON payloads must be rejected before byte array allocation");
        assertTrue(handler.contains("PAYLOAD_TOO_LARGE"),
                "RCON callers need a deterministic response for oversized payloads");
        assertTrue(emulator.contains("register(\"rcon.max_payload_bytes\", \"65536\")"),
                "RCON max payload default must be registered before startup");
    }

    @Test
    void inboundByteBufIsReleasedFromFinallyBlock() throws Exception {
        String source = handlerSource();
        int finallyIndex = source.indexOf("finally");
        int releaseIndex = source.indexOf("data.release()");

        assertTrue(finallyIndex >= 0, "RCON channelRead must release inbound ByteBufs from a finally block");
        assertTrue(releaseIndex > finallyIndex, "RCON channelRead must release the inbound ByteBuf after finally starts");
    }

    @Test
    void rconWhitelistUsesSocketAddressInsteadOfStringSplitting() throws Exception {
        String source = handlerSource();

        assertTrue(source.contains("InetSocketAddress"),
                "RCON whitelist should resolve socket addresses instead of parsing remoteAddress.toString()");
        assertTrue(source.contains("getHostAddress()"),
                "RCON whitelist should compare the resolved host address");
        assertTrue(!source.contains(".toString().split(\":\")"),
                "RCON whitelist must not split host:port strings because that breaks IPv6 addresses");
    }
}
