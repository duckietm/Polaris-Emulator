package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What loading a user's stats reads: the row of the stats table, then one query for each list that belongs to the user
 * (favorite rooms, ignored and blocked users, the word filter, the Discord settings).
 */
class HabboStatsLoadTest {
    private static final int USER_ID = 7;

    private Field environmentField;
    private Object previousEnvironment;
    private final Map<String, List<Map<String, Object>>> rowsBySql = new HashMap<>();
    private final List<String> prepared = new ArrayList<>();
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        this.environmentField = Emulator.class.getDeclaredField("gameEnvironment");
        this.environmentField.setAccessible(true);
        this.previousEnvironment = this.environmentField.get(null);

        GameEnvironment environment = mock(GameEnvironment.class);
        SubscriptionManager subscriptions = mock(SubscriptionManager.class);
        when(environment.getSubscriptionManager()).thenReturn(subscriptions);
        when(subscriptions.getSubscriptionsForUser(anyInt())).thenReturn(new HashSet<>());
        this.environmentField.set(null, environment);

        this.connection = mock(Connection.class);
        when(this.connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            this.prepared.add(sql);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeQuery()).thenAnswer(query -> rows(this.rowsFor(sql)));
            when(statement.getConnection()).thenReturn(this.connection);
            return statement;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        this.environmentField.set(null, this.previousEnvironment);
    }

    @Test
    void theListsOfAUserAreReadFromTheirOwnQueries() throws Exception {
        this.rowsBySql.put("users_favorite_rooms", List.of(Map.of("room_id", 11), Map.of("room_id", 12)));
        this.rowsBySql.put("users_ignored", List.of(Map.of("1", 21)));
        this.rowsBySql.put("users_blocked", List.of(Map.of("1", 31), Map.of("1", 32)));
        this.rowsBySql.put("users_wordfilter", List.of(Map.of("1", "badword")));
        this.rowsBySql.put("user_window_settings", List.of());

        HabboStats stats = this.load();

        assertEquals(List.of(11, 12), stats.getFavoriteRooms());
        assertEquals(List.of(21), stats.getIgnoredUsers());
        assertEquals(List.of(31, 32), stats.getBlockedUsers());
        assertTrue(stats.getCustomWordFilter().contains("badword"));
        assertEquals(1, stats.getCustomWordFilter().size());
    }

    @Test
    void aUserWithoutWindowSettingsGetsARowAndTheDefaults() throws Exception {
        this.rowsBySql.put("user_window_settings", List.of());

        HabboStats stats = this.load();

        assertTrue(this.prepared.contains("INSERT INTO user_window_settings (user_id) VALUES (?)"));
        assertNotNull(stats.navigatorWindowSettings);
    }

    @Test
    void theDiscordSettingsTakeTheFirstRowOnly() throws Exception {
        this.rowsBySql.put("user_window_settings", List.of());
        this.rowsBySql.put(
                "users_discord_settings",
                List.of(
                        Map.of(
                                "preference_version", 3,
                                "show_habbo", "1",
                                "share_activity", "0",
                                "hide_in_hidden_rooms", "1",
                                "allow_joining", "0"),
                        Map.of(
                                "preference_version", 9,
                                "show_habbo", "0",
                                "share_activity", "1",
                                "hide_in_hidden_rooms", "0",
                                "allow_joining", "1")));

        HabboStats stats = this.load();

        assertEquals(3, stats.getDiscordPreferences().getVersion());
        assertTrue(stats.getDiscordPreferences().isShowHabbo());
        assertFalse(stats.getDiscordPreferences().isShareActivity());
        assertTrue(stats.getDiscordPreferences().isHideInHiddenRooms());
    }

    @Test
    void aUserWithNothingStoredKeepsEmptyListsAndNoDiscordSettings() throws Exception {
        this.rowsBySql.put("user_window_settings", List.of());

        HabboStats stats = this.load();

        assertTrue(stats.getFavoriteRooms().isEmpty());
        assertTrue(stats.getIgnoredUsers().isEmpty());
        assertTrue(stats.getBlockedUsers().isEmpty());
        assertEquals(
                DiscordPreferences.UNINITIALIZED.getVersion(),
                stats.getDiscordPreferences().getVersion());
        verify(this.connection, never()).prepareStatement("DELETE FROM users");
    }

    @Test
    void everyListIsReadForTheUserWhoseStatsAreLoaded() throws Exception {
        this.rowsBySql.put("user_window_settings", List.of());

        this.load();

        for (String table : List.of(
                "user_window_settings",
                "users_navigator_settings",
                "users_favorite_rooms",
                "users_recipes",
                "calendar_rewards_claimed",
                "catalog_items_limited",
                "users_ignored",
                "users_blocked",
                "users_discord_settings",
                "users_wordfilter",
                "users_target_offer_purchases",
                "room_enter_log")) {
            assertTrue(this.prepared.stream().anyMatch(sql -> sql.contains(table)), table + " is read");
        }
    }

    private HabboStats load() throws Exception {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(USER_ID);

        ResultSet statsRow = mock(ResultSet.class);
        when(statsRow.getString(anyString())).thenReturn("0");
        when(statsRow.getString("tags")).thenReturn("a;b");
        when(statsRow.getInt(anyString())).thenReturn(0);
        PreparedStatement owner = mock(PreparedStatement.class);
        when(owner.getConnection()).thenReturn(this.connection);
        when(statsRow.getStatement()).thenReturn(owner);

        Constructor<HabboStats> constructor = HabboStats.class.getDeclaredConstructor(ResultSet.class, HabboInfo.class);
        constructor.setAccessible(true);
        return constructor.newInstance(statsRow, info);
    }

    private List<Map<String, Object>> rowsFor(String sql) {
        return this.rowsBySql.entrySet().stream()
                .filter(entry -> sql.contains(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(List.of());
    }

    private static ResultSet rows(List<Map<String, Object>> rows) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        int[] cursor = {-1};
        when(set.next()).thenAnswer(call -> ++cursor[0] < rows.size());
        when(set.getInt(anyString())).thenAnswer(call -> value(rows, cursor[0], call.getArgument(0), 0));
        when(set.getString(anyString())).thenAnswer(call -> value(rows, cursor[0], call.getArgument(0), ""));
        when(set.getInt(anyInt()))
                .thenAnswer(call -> value(rows, cursor[0], String.valueOf((int) call.getArgument(0)), 0));
        when(set.getString(anyInt()))
                .thenAnswer(call -> value(rows, cursor[0], String.valueOf((int) call.getArgument(0)), ""));
        return set;
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(List<Map<String, Object>> rows, int index, String column, T fallback) {
        if (index < 0 || index >= rows.size()) return fallback;
        Object stored = rows.get(index).get(column);
        return (stored == null) ? fallback : (T) stored;
    }
}
