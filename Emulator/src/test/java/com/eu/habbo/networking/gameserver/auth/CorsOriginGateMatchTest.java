package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A "*example.com" whitelist entry also allowed any host that merely ended with those letters. */
class CorsOriginGateMatchTest {

    @Test
    void aWildcardAllowsTheDomainAndItsSubdomainsOnly() {
        assertTrue(CorsOriginGate.matches("example.com", "*.example.com"));
        assertTrue(CorsOriginGate.matches("play.example.com", "*.example.com"));
        assertTrue(CorsOriginGate.matches("play.example.com", "*example.com"));
        assertFalse(CorsOriginGate.matches("evilexample.com", "*example.com"));
        assertFalse(CorsOriginGate.matches("example.com.evil.test", "*.example.com"));
    }

    @Test
    void anExactEntryAndTheStarBehaveAsBefore() {
        assertTrue(CorsOriginGate.matches("localhost", "localhost"));
        assertTrue(CorsOriginGate.matches("PLAY.Example.com", " play.example.com "));
        assertFalse(CorsOriginGate.matches("other.test", "localhost"));
        assertTrue(CorsOriginGate.matches("anything.test", "*"));
        assertFalse(CorsOriginGate.matches("example.com", "*."));
    }
}
