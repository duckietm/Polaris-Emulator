package com.eu.habbo.messages.outgoing.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserProfileLevelTest {
    private static final String STEPS = "0,100,250,500";

    @Test
    void theFirstThresholdIsLevelOne() {
        UserProfileLevel level = UserProfileLevel.of(0, STEPS);

        assertEquals(1, level.level());
        assertEquals(100, level.nextLevelStart());
    }

    @Test
    void aLevelIsReachedExactlyAtItsThreshold() {
        assertEquals(2, UserProfileLevel.of(99 + 1, STEPS).level());
        assertEquals(2, UserProfileLevel.of(249, STEPS).level());
        assertEquals(250, UserProfileLevel.of(249, STEPS).nextLevelStart());
    }

    @Test
    void theTopLevelHasNoNextThreshold() {
        UserProfileLevel level = UserProfileLevel.of(900, STEPS);

        assertEquals(4, level.level());
        assertEquals(900, level.nextLevelStart());
    }

    @Test
    void aBrokenListFallsBackToTheDefaultOne() {
        UserProfileLevel expected = UserProfileLevel.of(600, UserProfileLevel.DEFAULT_THRESHOLDS);

        assertEquals(expected.level(), UserProfileLevel.of(600, "0,abc,10").level());
        assertEquals(expected.level(), UserProfileLevel.of(600, "0,500,300").level());
        assertEquals(expected.level(), UserProfileLevel.of(600, "").level());
    }
}
