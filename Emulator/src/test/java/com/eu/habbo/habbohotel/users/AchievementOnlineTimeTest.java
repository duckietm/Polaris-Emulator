package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class AchievementOnlineTimeTest {
    @Test
    void combinesSavedPartialMinutesWithActualElapsedSessionTime() throws Exception {
        HabboStats stats = mock(HabboStats.class, CALLS_REAL_METHODS);
        var previous = HabboStats.class.getDeclaredField("previousOnlineTime");
        previous.setAccessible(true);
        previous.setInt(stats, 59);
        var started = HabboStats.class.getDeclaredField("sessionStartedAt");
        started.setAccessible(true);
        started.setInt(stats, 1000);
        assertEquals(1, stats.getOnlineMinutes(1001));
        assertEquals(11, stats.getOnlineMinutes(1601));
        assertEquals(0, stats.getOnlineMinutes(999));

        previous.setInt(stats, 659);
        started.setInt(stats, 2000);
        assertEquals(11, stats.getOnlineMinutes(2001));
    }

    @Test
    void retriesUnsavedSessionSecondsAfterDatabaseFailure() throws Exception {
        HabboStats stats = mock(HabboStats.class, CALLS_REAL_METHODS);
        var lastOnlineTime = new java.util.concurrent.atomic.AtomicInteger(1000);
        set(stats, "lastOnlineTime", lastOnlineTime);
        set(stats, "habboInfo", new HabboInfo(42, 0));
        set(stats, "offerCache", new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        stats.chatColor = com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles.getBubble(0);
        set(stats, "navigatorWindowSettings", new HabboNavigatorWindowSettings(42));
        var database = mock(com.eu.habbo.database.Database.class);
        var dataSource = mock(com.zaxxer.hikari.HikariDataSource.class);
        var connection = mock(java.sql.Connection.class);
        var statement = mock(java.sql.PreparedStatement.class);
        org.mockito.Mockito.when(database.getDataSource()).thenReturn(dataSource);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        org.mockito.Mockito.when(statement.executeUpdate())
                .thenThrow(new java.sql.SQLException("unavailable"))
                .thenReturn(1);
        try (var emulator = org.mockito.Mockito.mockStatic(com.eu.habbo.Emulator.class)) {
            emulator.when(com.eu.habbo.Emulator::getDatabase).thenReturn(database);
            emulator.when(com.eu.habbo.Emulator::getIntUnixTimestamp).thenReturn(1100, 1300);
            stats.run();
            assertEquals(1000, lastOnlineTime.get());
            stats.run();
            assertEquals(1300, lastOnlineTime.get());
            org.mockito.Mockito.verify(statement).setInt(7, 100);
            org.mockito.Mockito.verify(statement).setInt(7, 300);
        }
    }

    private static void set(HabboStats stats, String name, Object value) throws Exception {
        var field = HabboStats.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(stats, value);
    }
}
