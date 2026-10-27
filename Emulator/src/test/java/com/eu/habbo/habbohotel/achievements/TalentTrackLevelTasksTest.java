package com.eu.habbo.habbohotel.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboStats;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A talent level names its tasks by achievement id or name, so the rows do not depend on how a hotel numbers its
 * achievements; a level with a task the server cannot find is never completed.
 */
class TalentTrackLevelTasksTest {

    @Test
    void aTaskIsAnIdOrANameWithOrWithoutTheBadgePrefix() {
        AchievementManager manager = mock(AchievementManager.class);
        Achievement quiz = mock(Achievement.class);
        when(manager.getAchievement(1705)).thenReturn(quiz);
        when(manager.getAchievement("SafetyQuizGraduate")).thenReturn(quiz);

        assertSame(quiz, TalentTrackLevel.resolveAchievement(manager, "1705"));
        assertSame(quiz, TalentTrackLevel.resolveAchievement(manager, "SafetyQuizGraduate"));
        assertSame(quiz, TalentTrackLevel.resolveAchievement(manager, "ACH_SafetyQuizGraduate"));
        assertNull(TalentTrackLevel.resolveAchievement(manager, "Unknown"));
        assertNull(TalentTrackLevel.resolveAchievement(null, "1705"));
    }

    @Test
    void aLevelWithAMissingTaskIsNeverCompleted() throws Exception {
        AchievementManager manager = new AchievementManager();
        LinkedHashMap<Integer, TalentTrackLevel> levels = new LinkedHashMap<>();
        levels.put(0, level(0, false));
        levels.put(1, level(1, true));
        levels.put(2, level(2, false));
        trackLevels(manager).put(TalentTrackType.HELPER, levels);

        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));

        // Level 0 has no tasks and is complete; level 1 lists a task the server does not know, so it stops there.
        assertEquals(0, manager.calculateTalenTrackLevel(habbo, TalentTrackType.HELPER).level);
    }

    @Test
    void aLevelWithoutTasksLoadsWithoutTheGameEnvironment() throws Exception {
        // A club-only milestone (AchievementRewardsIT): no tasks, no furni; there is no game environment to ask.
        ResultSet row = mock(ResultSet.class);
        when(row.getString("type")).thenReturn("citizenship");
        when(row.getInt("level")).thenReturn(20);
        when(row.getInt("reward_hc_days")).thenReturn(2);
        when(row.getString("achievement_ids")).thenReturn("");
        when(row.getString("achievement_levels")).thenReturn("");
        when(row.getString("reward_furni")).thenReturn(" , ");
        when(row.getString("reward_perks")).thenReturn("TRADE");
        when(row.getString("reward_badges")).thenReturn("");

        TalentTrackLevel level = new TalentTrackLevel(row);

        assertEquals(2, level.hcDays);
        assertTrue(level.achievements.isEmpty());
        assertTrue(level.items.isEmpty());
        assertFalse(level.missingAchievements);
    }

    private static TalentTrackLevel level(int number, boolean missing) {
        TalentTrackLevel level = mock(TalentTrackLevel.class);
        level.level = number;
        level.type = TalentTrackType.HELPER;
        level.achievements = new HashMap<>();
        level.missingAchievements = missing;
        return level;
    }

    @SuppressWarnings("unchecked")
    private static Map<TalentTrackType, LinkedHashMap<Integer, TalentTrackLevel>> trackLevels(
            AchievementManager manager) throws Exception {
        Field field = AchievementManager.class.getDeclaredField("talentTrackLevels");
        field.setAccessible(true);
        return (Map<TalentTrackType, LinkedHashMap<Integer, TalentTrackLevel>>) field.get(manager);
    }
}
