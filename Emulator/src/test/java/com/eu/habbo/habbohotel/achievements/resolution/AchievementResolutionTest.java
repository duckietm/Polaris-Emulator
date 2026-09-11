package com.eu.habbo.habbohotel.achievements.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AchievementResolutionTest {
    private static AchievementResolution open(int endsAt) {
        return new AchievementResolution(1, 2, 3, 4, "ACH_Foo4", 100, endsAt, 0);
    }

    @Test
    void anOpenPromiseExpiresOnceItsClockIsReached() {
        assertFalse(open(200).expired(199));
        assertTrue(open(200).expired(200));
        assertTrue(open(200).expired(500));
    }

    @Test
    void aKeptPromiseNeverExpires() {
        AchievementResolution kept = new AchievementResolution(1, 2, 3, 4, "ACH_Foo4", 100, 200, 150);

        assertTrue(kept.completed());
        assertFalse(kept.expired(5000));
    }

    @Test
    void aPromiseWithoutAClockNeverExpires() {
        assertFalse(open(0).expired(5000));
    }

    @Test
    void theCountdownNeverGoesNegative() {
        assertEquals(60, open(200).secondsLeft(140));
        assertEquals(0, open(200).secondsLeft(200));
        assertEquals(0, open(200).secondsLeft(9999));
    }
}
