package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProfileLevelTest {
    @Test
    void levelOneStartsAtZeroAndEndsAtOneHundred() {
        ProfileLevel level = ProfileLevel.forScore(0);

        assertEquals(1, level.getLevel());
        assertEquals(0, level.getLevelStart());
        assertEquals(100, level.getNextLevelStart());
    }

    @Test
    void thresholdsGrowByFiftyTimesLevelTimesPreviousLevel() {
        assertEquals(0, ProfileLevel.startOf(1));
        assertEquals(100, ProfileLevel.startOf(2));
        assertEquals(300, ProfileLevel.startOf(3));
        assertEquals(600, ProfileLevel.startOf(4));
        assertEquals(1000, ProfileLevel.startOf(5));
    }

    @Test
    void reachingAThresholdMovesToThatLevel() {
        assertEquals(1, ProfileLevel.forScore(99).getLevel());
        assertEquals(2, ProfileLevel.forScore(100).getLevel());
        assertEquals(3, ProfileLevel.forScore(424).getLevel());
        assertEquals(300, ProfileLevel.forScore(424).getLevelStart());
        assertEquals(600, ProfileLevel.forScore(424).getNextLevelStart());
    }

    @Test
    void negativeScoresClampToLevelOne() {
        assertEquals(1, ProfileLevel.forScore(-5).getLevel());
    }
}
