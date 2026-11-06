package polaris.hotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnowWarSkillLevelTest {

    @Test
    void startsAtLevelOneAndCapsAtThirty() {
        assertEquals(1, SnowWarSkillLevel.fromTotalScore(-5));
        assertEquals(1, SnowWarSkillLevel.fromTotalScore(0));
        assertEquals(1, SnowWarSkillLevel.fromTotalScore(49));
        assertEquals(2, SnowWarSkillLevel.fromTotalScore(50));
        assertEquals(3, SnowWarSkillLevel.fromTotalScore(200));
        assertEquals(30, SnowWarSkillLevel.fromTotalScore(SnowWarSkillLevel.scoreForLevel(30)));
        assertEquals(30, SnowWarSkillLevel.fromTotalScore(Integer.MAX_VALUE));
    }

    @Test
    void levelsAreMonotonicAndConsistentWithThresholds() {
        int previous = 1;
        for (int score = 0; score < 60_000; score += 7) {
            int level = SnowWarSkillLevel.fromTotalScore(score);
            assertTrue(level >= previous, "score " + score);
            previous = level;
        }
        for (int level = 1; level <= 30; level++) {
            assertEquals(level, SnowWarSkillLevel.fromTotalScore(SnowWarSkillLevel.scoreForLevel(level)));
        }
    }

    @Test
    void reportsPointsToNextLevel() {
        assertEquals(50, SnowWarSkillLevel.scoreToNextLevel(0));
        assertEquals(1, SnowWarSkillLevel.scoreToNextLevel(49));
        assertEquals(150, SnowWarSkillLevel.scoreToNextLevel(50));
        assertEquals(0, SnowWarSkillLevel.scoreToNextLevel(SnowWarSkillLevel.scoreForLevel(30)));
    }
}
