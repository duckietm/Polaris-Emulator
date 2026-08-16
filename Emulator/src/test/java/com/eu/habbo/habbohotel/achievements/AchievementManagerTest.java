package com.eu.habbo.habbohotel.achievements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AchievementManagerTest {
    @Test
    void matchesBadgeCodesOfTheAchievementLineage() {
        assertTrue(AchievementManager.isAchievementBadge("ACH_PetRespectGiver1", "PetRespectGiver"));
        assertTrue(AchievementManager.isAchievementBadge("ACH_PetRespectGiver13", "PetRespectGiver"));
    }

    @Test
    void matchesRegardlessOfCase() {
        assertTrue(AchievementManager.isAchievementBadge("ach_petrespectgiver2", "PetRespectGiver"));
        assertTrue(AchievementManager.isAchievementBadge("ACH_PETRESPECTGIVER2", "petrespectgiver"));
    }

    @Test
    void rejectsOtherAchievementsSharingThePrefix() {
        assertFalse(AchievementManager.isAchievementBadge("ACH_RoomEntryFriend5", "RoomEntry"));
    }

    @Test
    void rejectsCodesWithoutATrailingLevel() {
        assertFalse(AchievementManager.isAchievementBadge("ACH_PetRespectGiver", "PetRespectGiver"));
        assertFalse(AchievementManager.isAchievementBadge("ACH_PetRespectGiver1b", "PetRespectGiver"));
    }

    @Test
    void rejectsUnrelatedCodesAndNulls() {
        assertFalse(AchievementManager.isAchievementBadge("ADM", "PetRespectGiver"));
        assertFalse(AchievementManager.isAchievementBadge(null, "PetRespectGiver"));
        assertFalse(AchievementManager.isAchievementBadge("ACH_PetRespectGiver1", null));
    }
}
