package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** AIR stores a per-game limit above 10 as 10; 0 or less stays unlimited. */
class WiredScorePerGameLimitTest {
    @Test
    void aLimitAboveTenIsTen() {
        assertEquals(10, WiredScorePerGame.normalize(99));
        assertEquals(5, WiredScorePerGame.normalize(5));
        assertEquals(0, WiredScorePerGame.normalize(0));
        assertEquals(0, WiredScorePerGame.normalize(-3));
    }
}
