package com.eu.habbo.habbohotel.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuildBadgeChangeLimiterTest {
    @Test
    void spacesOutChangesPerGuild() {
        GuildBadgeChangeLimiter limiter = new GuildBadgeChangeLimiter();

        assertTrue(limiter.tryChange(1, 3000, 10_000));
        assertFalse(limiter.tryChange(1, 3000, 11_000));
        assertTrue(limiter.tryChange(2, 3000, 11_000), "another guild");
        assertTrue(limiter.tryChange(1, 3000, 13_000));
        assertTrue(limiter.tryChange(1, 0, 13_001), "no cooldown");
    }
}
