package com.eu.habbo.habbohotel.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.users.achievements.UserAchievementLeveledEvent;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchievementRewardsIT {
    private static HikariDataSource dataSource;
    private Achievement achievement;

    @BeforeAll
    static void database() throws Exception {
        assumeTrue(TestDatabase.dockerAvailable(), "Docker required for MariaDB integration tests");
        dataSource = TestDatabase.freshDatabase("achievement_rewards");
        MigrationRunner.migrate(dataSource);
    }

    @AfterAll
    static void close() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void seed() throws Exception {
        execute("DELETE FROM users_achievements_queue WHERE user_id = 910020");
        execute("DELETE FROM users_achievements WHERE user_id = 910020");
        execute("DELETE FROM users_badges WHERE user_id = 910020");
        execute("DELETE FROM items WHERE user_id = 910020");
        execute("DELETE FROM users_subscriptions WHERE user_id = 910020");
        execute("TRUNCATE TABLE logs_economy");
        execute("DELETE FROM users_currency WHERE user_id = 910020");
        execute("DELETE FROM users_settings WHERE user_id = 910020");
        execute("DELETE FROM users WHERE id = 910020");
        execute("DELETE FROM achievements WHERE name = 'AtomicReward'");
        execute(
                "INSERT INTO users (id, username, password, ip_register, ip_current, credits) VALUES (910020, 'achievement_it', '!', '127.0.0.1', '127.0.0.1', 20)");
        execute("INSERT INTO users_settings (user_id, achievement_score) VALUES (910020, 0)");
        execute(
                "INSERT INTO achievements (id,name,category,level,reward_amount,reward_type,points,progress_needed) VALUES (990001,'AtomicReward','identity',1,7,0,11,2), (990002,'AtomicReward','identity',2,13,5,17,4), (990003,'AtomicReward','identity',3,19,-1,23,6)");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT * FROM achievements WHERE name = 'AtomicReward' ORDER BY level")) {
            result.next();
            achievement = new Achievement(result);
            while (result.next()) achievement.addLevel(new AchievementLevel(result));
        }
    }

    @Test
    void crossingSeveralLevelsPaysEveryConfiguredRewardAndCannotReplay() throws Exception {
        withOnlineUser(
                habbo -> {
                    AchievementManager.progressAchievement(habbo, achievement, 6);
                    AchievementManager.progressAchievement(habbo, achievement, 6);
                    assertEquals(6, habbo.getHabboStats().getAchievementProgress(achievement));
                    assertEquals(51, habbo.getHabboStats().achievementScore);
                    assertEquals(
                            List.of("ACH_AtomicReward3"),
                            habbo.getInventory().getBadgesComponent().getBadgesSnapshot().stream()
                                    .map(HabboBadge::getCode)
                                    .toList());
                },
                false);
        assertEquals(6, value("SELECT progress FROM users_achievements WHERE user_id = 910020"));
        assertEquals(51, value("SELECT achievement_score FROM users_settings WHERE user_id = 910020"));
        assertEquals(7, value("SELECT amount FROM users_currency WHERE user_id = 910020 AND type = 0"));
        assertEquals(13, value("SELECT amount FROM users_currency WHERE user_id = 910020 AND type = 5"));
        assertEquals(39, value("SELECT credits FROM users WHERE id = 910020"));
        assertEquals(3, value("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910020"));
        assertEquals(
                1,
                value("SELECT COUNT(*) FROM users_badges WHERE user_id = 910020 AND badge_code = 'ACH_AtomicReward3'"));
    }

    @Test
    void normalAchievementPreservesUnsavedWiredAndPluginExperience() throws Exception {
        execute("UPDATE users_settings SET achievement_score = 100 WHERE user_id = 910020");
        withOnlineUser(
                habbo -> {
                    habbo.getHabboStats().achievementScore = 100;
                    habbo.getHabboStats().addAchievementScore(50);
                    AchievementManager.progressAchievement(habbo, achievement, 2);
                    assertEquals(161, habbo.getHabboStats().getAchievementScore());
                },
                false);
        assertEquals(161, value("SELECT achievement_score FROM users_settings WHERE user_id = 910020"));
    }

    @Test
    void cancelledLevelUpLeavesProgressWalletAndBadgeUnchanged() throws Exception {
        withOnlineUser(habbo -> AchievementManager.progressAchievement(habbo, achievement, 6), true);
        assertEquals(0, value("SELECT COUNT(*) FROM users_achievements WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM users_badges WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910020"));
    }

    @Test
    void inactiveAndMalformedProgressCannotGrantRewards() throws Exception {
        withOnlineUser(
                habbo -> {
                    AchievementManager.progressAchievement(habbo, achievement, -1);
                    AchievementManager.progressAchievement(habbo, achievement, 0);
                    for (short state : new short[] {0, 2, 3}) {
                        achievement.state = state;
                        AchievementManager.progressAchievement(habbo, achievement, 6);
                    }
                },
                false);
        assertEquals(0, value("SELECT COUNT(*) FROM users_achievements WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM logs_economy WHERE user_id = 910020"));
    }

    @Test
    void rewardFailureRollsBackProgressScoreAndOfflineQueue() throws Exception {
        execute("INSERT INTO users_achievements_queue (user_id,achievement_id,amount) VALUES (910020,990001,6)");
        try (Connection connection = dataSource.getConnection()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> AchievementRewards.persist(
                            connection,
                            910020,
                            achievement,
                            0,
                            6,
                            levels(),
                            List.of(operation(1, 0, 7), operation(2, -1, -100)),
                            6,
                            0));
        }
        assertEquals(0, value("SELECT COUNT(*) FROM users_achievements WHERE user_id = 910020"));
        assertEquals(0, value("SELECT achievement_score FROM users_settings WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM users_currency WHERE user_id = 910020"));
        assertEquals(6, value("SELECT amount FROM users_achievements_queue WHERE user_id = 910020"));
    }

    @Test
    void successfulQueueConsumptionPreservesNewlyQueuedProgressAndBadgeSlot() throws Exception {
        execute("INSERT INTO users_achievements_queue (user_id,achievement_id,amount) VALUES (910020,990001,9)");
        execute(
                "INSERT INTO users_badges (user_id,badge_code,slot_id) VALUES (910020,'ACH_AtomicReward1',3), (910020,'ACH_AtomicRewardFriend5',2)");
        try (Connection connection = dataSource.getConnection()) {
            var result = AchievementRewards.persist(
                    connection, 910020, achievement, 0, 6, levels(), List.of(operation(1, 0, 7)), 6, 0);
            assertEquals(3, result.badge().slot());
        }
        assertEquals(3, value("SELECT amount FROM users_achievements_queue WHERE user_id = 910020"));
        assertEquals(
                1,
                value(
                        "SELECT COUNT(*) FROM users_badges WHERE user_id = 910020 AND badge_code = 'ACH_AtomicRewardFriend5'"));
    }

    @Test
    void concurrentDuplicateTransitionCannotAwardTwice() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                attempts.add(executor.submit(() -> {
                    try (Connection connection = dataSource.getConnection()) {
                        AchievementRewards.persist(
                                connection, 910020, achievement, 0, 6, levels(), List.of(operation(1, 0, 7)), 0, 0);
                        return true;
                    } catch (SQLException staleProgress) {
                        return false;
                    }
                }));
            }
            assertNotEquals(attempts.get(0).get(), attempts.get(1).get());
        }
        assertEquals(51, value("SELECT achievement_score FROM users_settings WHERE user_id = 910020"));
        assertEquals(7, value("SELECT amount FROM users_currency WHERE user_id = 910020 AND type = 0"));
    }

    @Test
    void talentMilestoneCommitsFurnitureBadgeAndPerkOnce() throws Exception {
        var level = talentLevel();
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(910020);
        when(habbo.getHabboInfo()).thenReturn(info);
        var itemManager = mock(com.eu.habbo.habbohotel.items.ItemManager.class, CALLS_REAL_METHODS);
        int previous = value("SELECT talent_track_citizenship_level FROM users_settings WHERE user_id = 910020");
        try (Connection connection = dataSource.getConnection()) {
            var result = TalentTrackRewards.persist(
                    connection,
                    habbo,
                    itemManager,
                    new com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager(),
                    TalentTrackType.CITIZENSHIP,
                    previous,
                    3,
                    List.of(level),
                    1000);
            assertEquals(1, result.items().size());
            assertEquals(1, result.badges().size());
            assertTrue(result.trade());
            assertEquals(172800, result.subscription().getDuration());
        }
        try (Connection connection = dataSource.getConnection()) {
            assertThrows(
                    SQLException.class,
                    () -> TalentTrackRewards.persist(
                            connection,
                            habbo,
                            itemManager,
                            new com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager(),
                            TalentTrackType.CITIZENSHIP,
                            previous,
                            3,
                            List.of(level),
                            1000));
        }
        assertEquals(1, value("SELECT COUNT(*) FROM items WHERE user_id = 910020"));
        assertEquals(3, value("SELECT talent_track_citizenship_level FROM users_settings WHERE user_id = 910020"));
        assertEquals(1, value("SELECT perk_trade FROM users_settings WHERE user_id = 910020"));
        assertEquals(1, value("SELECT COUNT(*) FROM users_subscriptions WHERE user_id = 910020"));
        assertEquals(172800, value("SELECT duration FROM users_subscriptions WHERE user_id = 910020"));
    }

    @Test
    void failedTalentFurnitureRollsBackTheMilestone() throws Exception {
        var level = talentLevel();
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(910020);
        when(habbo.getHabboInfo()).thenReturn(info);
        var itemManager = mock(com.eu.habbo.habbohotel.items.ItemManager.class);
        int previous = value("SELECT talent_track_citizenship_level FROM users_settings WHERE user_id = 910020");
        try (Connection connection = dataSource.getConnection()) {
            assertThrows(
                    SQLException.class,
                    () -> TalentTrackRewards.persist(
                            connection,
                            habbo,
                            itemManager,
                            new com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager(),
                            TalentTrackType.CITIZENSHIP,
                            previous,
                            3,
                            List.of(level),
                            1000));
        }
        assertEquals(
                previous, value("SELECT talent_track_citizenship_level FROM users_settings WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM users_badges WHERE user_id = 910020"));
        assertEquals(0, value("SELECT COUNT(*) FROM users_subscriptions WHERE user_id = 910020"));
    }

    @Test
    void talentClubDaysExtendTheActiveSubscription() throws Exception {
        execute(
                "INSERT INTO users_subscriptions (user_id, subscription_type, timestamp_start, duration, active) VALUES (910020,'HABBO_CLUB',500,86400,1)");
        var level = talentLevel();
        level.items = Set.of();
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(910020);
        when(habbo.getHabboInfo()).thenReturn(info);
        int previous = value("SELECT talent_track_citizenship_level FROM users_settings WHERE user_id = 910020");
        try (Connection connection = dataSource.getConnection()) {
            var result = TalentTrackRewards.persist(
                    connection,
                    habbo,
                    null,
                    new com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager(),
                    TalentTrackType.CITIZENSHIP,
                    previous,
                    3,
                    List.of(level),
                    1000);
            assertEquals(259200, result.subscription().getDuration());
            assertEquals(500, result.subscription().getTimestampStart());
        }
        assertEquals(1, value("SELECT COUNT(*) FROM users_subscriptions WHERE user_id = 910020"));
    }

    @Test
    void loadsClubOnlyMilestonesWithoutFurniture() throws Exception {
        execute(
                "INSERT INTO achievements_talents (id,type,level,achievement_ids,achievement_levels,reward_furni,reward_perks,reward_badges,reward_hc_days) VALUES (990001,'citizenship',20,'','',' , ','TRADE','',2)");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT * FROM achievements_talents WHERE id = 990001")) {
            result.next();
            var level = new TalentTrackLevel(result);
            assertEquals(2, level.hcDays);
            assertTrue(level.items.isEmpty());
        }
    }

    private TalentTrackLevel talentLevel() {
        var level = mock(TalentTrackLevel.class);
        level.level = 3;
        level.hcDays = 2;
        level.perks = new String[] {"TRADE"};
        level.badges = new String[] {"TALENT_TEST"};
        var item = mock(com.eu.habbo.habbohotel.items.Item.class);
        when(item.getId()).thenReturn(1);
        when(item.getName()).thenReturn("talent_chair");
        when(item.getInteractionType())
                .thenReturn(new com.eu.habbo.habbohotel.items.ItemInteraction(
                        "default", com.eu.habbo.habbohotel.items.interactions.InteractionDefault.class));
        level.items = Set.of(item);
        return level;
    }

    private void withOnlineUser(java.util.function.Consumer<Habbo> action, boolean cancelLevel) throws Exception {
        Habbo habbo = mock(Habbo.class);
        var constructor = HabboInfo.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        HabboInfo info = constructor.newInstance(910020, 20);
        HabboStats stats = mock(HabboStats.class);
        HabboInventory inventory = mock(HabboInventory.class);
        BadgesComponent badges = mock(BadgesComponent.class);
        Set<HabboBadge> ownedBadges = new HashSet<>();
        Map<Achievement, Integer> progress = new HashMap<>();
        when(stats.getAchievementScore()).thenAnswer(call -> stats.achievementScore);
        doCallRealMethod().when(stats).addAchievementScore(anyInt());
        when(stats.getAchievementProgress(any())).thenAnswer(call -> progress.getOrDefault(call.getArgument(0), -1));
        doAnswer(call -> {
                    progress.put(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .when(stats)
                .setProgress(any(), anyInt());
        when(badges.getBadgesSnapshot()).thenAnswer(call -> new ArrayList<>(ownedBadges));
        doAnswer(call -> {
                    ownedBadges.add(call.getArgument(0));
                    return null;
                })
                .when(badges)
                .addBadge(any());
        when(inventory.getBadgesComponent()).thenReturn(badges);
        when(habbo.getInventory()).thenReturn(inventory);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getClient()).thenReturn(mock(GameClient.class));
        when(habbo.isOnline()).thenReturn(true);
        Database database = mock(Database.class);
        when(database.getDataSource()).thenReturn(dataSource);
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(true);
        when(plugins.fireEvent(any())).thenAnswer(call -> {
            com.eu.habbo.plugin.Event event = call.getArgument(0);
            if (cancelLevel && event instanceof UserAchievementLeveledEvent) event.setCancelled(true);
            return event;
        });
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);
            action.accept(habbo);
        }
    }

    private List<AchievementLevel> levels() {
        return achievement.levels.values().stream()
                .sorted(Comparator.comparingInt(level -> level.level))
                .toList();
    }

    private EconomyOperation operation(int level, int currency, int amount) {
        return new EconomyOperation(
                "achievement:910020:990001:" + level,
                910020,
                910020,
                "achievement_reward",
                "achievement.level",
                currency,
                amount,
                null,
                "AtomicReward");
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int value(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
