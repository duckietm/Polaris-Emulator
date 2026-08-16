package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HousekeepingRankPolicyTest {
    @Test
    void highestConfiguredRankCanTargetAPeer() {
        HousekeepingRankPolicy.Decision decision = HousekeepingRankPolicy.evaluate(7, 7, 7);

        assertTrue(decision.allowed());
        assertEquals(HousekeepingRankPolicy.Reason.ALLOWED_CORE_PEER, decision.reason());
    }

    @Test
    void regularStaffCannotTargetAPeer() {
        HousekeepingRankPolicy.Decision decision = HousekeepingRankPolicy.evaluate(6, 6, 7);

        assertFalse(decision.allowed());
        assertEquals(HousekeepingRankPolicy.Reason.TARGET_NOT_LOWER, decision.reason());
    }

    @Test
    void highestConfiguredRankCannotTargetAHigherRank() {
        HousekeepingRankPolicy.Decision decision = HousekeepingRankPolicy.evaluate(7, 8, 7);

        assertFalse(decision.allowed());
        assertEquals(HousekeepingRankPolicy.Reason.TARGET_NOT_LOWER, decision.reason());
    }

    @Test
    void higherStaffRankCanTargetALowerRank() {
        HousekeepingRankPolicy.Decision decision = HousekeepingRankPolicy.evaluate(6, 5, 7);

        assertTrue(decision.allowed());
        assertEquals(HousekeepingRankPolicy.Reason.ALLOWED_LOWER_RANK, decision.reason());
    }

    @Test
    void invalidRankContextIsRejected() {
        HousekeepingRankPolicy.Decision decision = HousekeepingRankPolicy.evaluate(0, 7, 7);

        assertFalse(decision.allowed());
        assertEquals(HousekeepingRankPolicy.Reason.INVALID_RANK_CONTEXT, decision.reason());
    }
}
