package com.eu.habbo.networking.rconserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The RCON allowlist compared text, so "::1" never matched the address Netty reports for it. */
class RCONAllowlistTest {

    @Test
    void anAddressEntryMatchesEveryFormOfThatAddress() {
        assertTrue(RCONServerHandler.isAllowed("0:0:0:0:0:0:0:1", List.of("127.0.0.1", "::1")));
        assertTrue(RCONServerHandler.isAllowed("127.0.0.1", List.of(" 127.0.0.1 ")));
    }

    @Test
    void anotherAddressIsRefused() {
        assertFalse(RCONServerHandler.isAllowed("10.0.0.5", List.of("127.0.0.1", "::1")));
        assertFalse(RCONServerHandler.isAllowed("0:0:0:0:0:0:0:2", List.of("::1")));
    }

    @Test
    void aNameEntryIsStillComparedAsText() {
        assertTrue(RCONServerHandler.isAllowed("cms.local", List.of("cms.local")));
        assertFalse(RCONServerHandler.isAllowed("127.0.0.1", List.of("localhost")));
    }

    @Test
    void anInvalidMessageLogsOnlyItsKey() throws Exception {
        String handler = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServerHandler.java"));

        assertTrue(handler.contains("LOGGER.error(\"Invalid RCON Message: {}\", key);"));
        assertFalse(handler.contains("LOGGER.error(\"Invalid RCON Message: {}\", message);"));
    }
}
