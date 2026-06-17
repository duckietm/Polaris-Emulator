package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SetRankRequestGuardTest {
    @Test
    void acceptsKnownRanksWithinTheRconCeiling() {
        assertNull(SetRankRequestGuard.validate(1, 5, 12, rankId -> rankId == 5));
    }

    @Test
    void rejectsInvalidUsersRanksAndUnknownRanks() {
        assertEquals("invalid user", SetRankRequestGuard.validate(0, 5, 12, rankId -> true));
        assertEquals("invalid rank", SetRankRequestGuard.validate(1, 0, 12, rankId -> true));
        assertEquals("invalid rank", SetRankRequestGuard.validate(1, 5, 12, rankId -> false));
    }

    @Test
    void rejectsRanksAboveConfiguredCeiling() {
        assertEquals("rank exceeds rcon ceiling", SetRankRequestGuard.validate(1, 13, 12, rankId -> true));
    }

    @Test
    void parsesInvalidMaxRankAsDefaultCeiling() {
        assertEquals(SetRankRequestGuard.DEFAULT_MAX_RANK, SetRankRequestGuard.parseMaxRank(null));
        assertEquals(SetRankRequestGuard.DEFAULT_MAX_RANK, SetRankRequestGuard.parseMaxRank("0"));
        assertEquals(7, SetRankRequestGuard.parseMaxRank("7"));
    }
}
