package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A whitelist entry ignored the origin's scheme and port, so another service on the same host could read the API
 * answers; and a ban left the access tokens already issued working until they expired.
 */
class CorsSchemeAndBanTokensTest {

    @Test
    void anEntryWithItsSchemeAlsoNeedsTheSchemeAndPort() {
        URI https = URI.create("https://hotel.com");
        URI http = URI.create("http://hotel.com");
        URI otherPort = URI.create("https://hotel.com:8443");

        assertTrue(CorsOriginGate.matchesOrigin(https, "hotel.com", "https://hotel.com"));
        assertTrue(CorsOriginGate.matchesOrigin(URI.create("https://hotel.com:443"), "hotel.com", "https://hotel.com"));
        assertFalse(CorsOriginGate.matchesOrigin(http, "hotel.com", "https://hotel.com"));
        assertFalse(CorsOriginGate.matchesOrigin(otherPort, "hotel.com", "https://hotel.com"));
        assertTrue(CorsOriginGate.matchesOrigin(otherPort, "hotel.com", "https://hotel.com:8443"));
    }

    @Test
    void anEntryWithTheHostAloneMatchesAsBefore() {
        assertTrue(CorsOriginGate.matchesOrigin(URI.create("http://hotel.com:8080"), "hotel.com", "hotel.com"));
        assertTrue(CorsOriginGate.matchesOrigin(URI.create("https://www.hotel.com"), "hotel.com", "*.hotel.com"));
    }

    @Test
    void writingABanRevokesTheUsersTokens() throws Exception {
        Path modtool = Path.of("src/main/java/com/eu/habbo/habbohotel/modtool");

        assertTrue(Files.readString(modtool.resolve("ModToolBan.java"))
                .contains("AccessTokenService.revokeAll(connection, this.userId);"));
        assertTrue(Files.readString(modtool.resolve("ModToolManager.java"))
                .contains("AccessTokenService.revokeAll(connection, userId);"));
    }
}
