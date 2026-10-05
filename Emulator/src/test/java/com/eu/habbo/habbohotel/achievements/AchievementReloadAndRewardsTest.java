package com.eu.habbo.habbohotel.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AchievementReloadAndRewardsTest {

    private static Achievement achievement(int id, String name, AchievementLevel... levels) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(id);
        when(set.getString("name")).thenReturn(name);
        when(set.getString("category")).thenReturn("identity");
        when(set.getInt("level")).thenReturn(levels[0].level);
        when(set.getInt("progress_needed")).thenReturn(levels[0].progress);
        when(set.getShort("state")).thenReturn((short) 1);
        when(set.getString("subcategory")).thenReturn("");
        Achievement achievement = new Achievement(set);
        for (int index = 1; index < levels.length; index++) achievement.addLevel(levels[index]);
        return achievement;
    }

    private static AchievementLevel level(int level, int progress) {
        return new AchievementLevel(level, 0, 0, 10, progress);
    }

    @Test
    void reloadKeepsIdentitySwapsLevelsAndDropsDeletedAchievements() throws Exception {
        AchievementManager manager = new AchievementManager();
        Achievement live = achievement(1, "RoomEntry", level(1, 1), level(2, 5));
        Achievement deleted = achievement(2, "Gone", level(1, 1));
        manager.getAchievements().put(live.name, live);
        manager.getAchievements().put(deleted.name, deleted);

        Map<String, Achievement> loaded = new LinkedHashMap<>();
        loaded.put("RoomEntry", achievement(1, "RoomEntry", level(1, 1), level(2, 4), level(3, 9)));
        loaded.put("Fresh", achievement(3, "Fresh", level(1, 2)));
        manager.applyLoaded(loaded);

        assertSame(live, manager.getAchievement("RoomEntry"), "online progress is keyed by the instance");
        assertEquals(List.of(1, 2, 3), List.copyOf(live.levels().keySet()));
        assertEquals(4, live.levels.get(2).progress, "the public view reads the new set");
        assertNull(manager.getAchievement("Gone"));
        assertNull(manager.getAchievement(2));
        assertNotNull(manager.getAchievement("Fresh"));
    }

    @Test
    void readersAlwaysSeeACompleteLevelSetWhileLevelsAreSwapped() throws Exception {
        Achievement achievement = achievement(1, "Chat", level(1, 1), level(2, 5), level(3, 10));
        List<AchievementLevel> first = List.copyOf(achievement.levels().values());
        List<AchievementLevel> second = List.of(level(1, 1), level(2, 6), level(3, 11));
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<String> failure = new AtomicReference<>();

        Thread reader = Thread.ofPlatform().start(() -> {
            while (running.get() && failure.get() == null) {
                AchievementLevel top = achievement.getLevelForProgress(50);
                if (top == null || top.level != 3) failure.set("missing top level: " + top);
                if (achievement.levels().size() != 3) failure.set("partial set");
            }
        });
        for (int round = 0; round < 20_000; round++) achievement.replaceLevels(round % 2 == 0 ? second : first);
        running.set(false);
        reader.join();

        assertNull(failure.get());
        assertThrows(
                UnsupportedOperationException.class, () -> achievement.levels().put(9, level(9, 99)));
    }

    @Test
    void oneProgressTransitionUsesOneSnapshot() throws Exception {
        Achievement achievement = achievement(1, "Chat", level(1, 1), level(2, 5));
        Map<Integer, AchievementLevel> snapshot = achievement.levels();
        achievement.replaceLevels(List.of(level(1, 100)));

        assertEquals(2, Achievement.levelForProgress(snapshot, 5).level);
        assertNull(achievement.getLevelForProgress(5));
    }

    @Test
    void aHigherManuallyGrantedBadgeIsKept() {
        assertEquals(5, AchievementRewards.badgeLevel("ACH_RoomEntry5", "RoomEntry"));
        assertEquals(12, AchievementRewards.badgeLevel("ach_roomentry12", "RoomEntry"));
        assertEquals(0, AchievementRewards.badgeLevel("ACH_RoomEntryFriend5", "RoomEntry"));
        assertEquals(0, AchievementRewards.badgeLevel("ACH_RoomEntry99999999999", "RoomEntry"));

        assertEquals("ACH_RoomEntry5", AchievementRewards.badgeCode("RoomEntry", 3, 5));
        assertEquals("ACH_RoomEntry4", AchievementRewards.badgeCode("RoomEntry", 4, 2));
        assertEquals("ACH_RoomEntry1", AchievementRewards.badgeCode("RoomEntry", 1, 0));
    }

    @Test
    void progressDuringTheDisconnectWindowIsQueuedByUserId() throws Exception {
        Achievement achievement = achievement(31, "Chat", level(1, 1));
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(42);
        when(habbo.getHabboInfo()).thenReturn(info);
        HabboStats stats = mock(HabboStats.class);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.isOnline()).thenReturn(false);

        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);

        Field field = Emulator.class.getDeclaredField("database");
        field.setAccessible(true);
        Object original = field.get(null);
        field.set(null, database);
        try {
            AchievementManager.progressAchievement(habbo, achievement, 3);
        } finally {
            field.set(null, original);
        }

        verify(connection).prepareStatement(contains("users_achievements_queue"));
        verify(statement).setInt(1, 42);
        verify(statement).setInt(2, 31);
        verify(statement).setInt(3, 3);
        verify(statement).execute();
        verify(stats, never()).setProgress(achievement, 3);
    }
}
