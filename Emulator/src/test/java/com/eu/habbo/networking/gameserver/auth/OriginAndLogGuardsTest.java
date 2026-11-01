package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The game socket checked its Origin with a plain suffix match ("evilhotel.com" passed "*hotel.com"), and a username
 * with a new line went into the login log as it came.
 */
class OriginAndLogGuardsTest {

    @Test
    void theSocketUsesTheCorsMatch() throws Exception {
        assertTrue(CorsOriginGate.matches("www.hotel.com", "*.hotel.com"));
        assertTrue(CorsOriginGate.matches("HOTEL.com", "*.hotel.com"));
        assertFalse(CorsOriginGate.matches("evilhotel.com", "*.hotel.com"));

        String handler = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/handlers/WebSocketHttpHandler.java"));
        assertTrue(handler.contains("CorsOriginGate.matches(toCheck, entry)"));
    }

    @Test
    void aClientValueCannotAddALogLine() {
        assertEquals("alice_ INFO fake", AuthHttpUtil.forLog("alice\n INFO fake"));
        assertEquals("", AuthHttpUtil.forLog(null));
        assertEquals(67, AuthHttpUtil.forLog("x".repeat(100)).length());
    }
}
