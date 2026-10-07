package com.eu.habbo.habbohotel.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseMath;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.outgoing.achievements.AchievementProgressComposer;
import com.eu.habbo.messages.outgoing.achievements.AchievementUnlockedComposer;
import com.eu.habbo.messages.outgoing.achievements.talenttrack.TalentLevelUpdateComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.users.UserAchievementScoreComposer;
import com.eu.habbo.messages.outgoing.users.UserBadgesComposer;
import com.eu.habbo.messages.outgoing.users.UserCitizinShipComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import com.eu.habbo.plugin.events.users.achievements.UserAchievementLeveledEvent;
import com.eu.habbo.plugin.events.users.achievements.UserAchievementProgressEvent;
import com.eu.habbo.plugin.events.users.subscriptions.UserSubscriptionCreatedEvent;
import com.eu.habbo.plugin.events.users.subscriptions.UserSubscriptionExtendedEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AchievementManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementManager.class);

    public static volatile boolean TALENTTRACK_ENABLED = false;

    private final Map<String, Achievement> achievements;
    private final Object reloadLock = new Object();
    private final Map<TalentTrackType, LinkedHashMap<Integer, TalentTrackLevel>> talentTrackLevels;

    public AchievementManager() {
        this.achievements = new ConcurrentHashMap<>();
        this.talentTrackLevels = new HashMap<>();
    }

    public static void progressAchievement(int habboId, Achievement achievement) {
        progressAchievement(habboId, achievement, 1);
    }

    public static void progressAchievement(int habboId, Achievement achievement, int amount) {
        if (achievement != null && amount > 0 && habboId > 0) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);

            if (habbo != null) {
                progressAchievement(habbo, achievement, amount);
            } else {
                queueOffline(habboId, achievement, amount);
            }
        }
    }

    /** Stores progress for a user that is not online; it is applied on their next login. */
    private static void queueOffline(int habboId, Achievement achievement, int amount) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(""
                        + "INSERT INTO users_achievements_queue (user_id, achievement_id, amount) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE amount = LEAST(2147483647, CAST(amount AS SIGNED) + ?)")) {
            statement.setInt(1, habboId);
            statement.setInt(2, achievement.id);
            statement.setInt(3, amount);
            statement.setInt(4, amount);
            statement.execute();
        } catch (SQLException | RuntimeException e) {
            LOGGER.error("Unable to queue achievement {} for user {}", achievement.name, habboId, e);
        }
    }

    public static void progressAchievement(Habbo habbo, Achievement achievement) {
        progressAchievement(habbo, achievement, 1);
    }

    public static void progressAchievement(Habbo habbo, Achievement achievement, int amount) {
        if (achievement == null || habbo == null || amount <= 0) return;
        if (!habbo.isOnline()) {
            // Logging in or out: keep the progress for the next login instead of dropping it.
            if (habbo.getHabboInfo() != null && habbo.getHabboInfo().getId() > 0)
                queueOffline(habbo.getHabboInfo().getId(), achievement, amount);
            return;
        }

        try {
            LedgerWalletMutation.coordinated(habbo, () -> {
                progressWhileCoordinated(habbo, achievement, amount, 0);
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to progress achievement {} for user {}",
                    achievement.name,
                    habbo.getHabboInfo().getId(),
                    exception);
        }
    }

    private static void progressWhileCoordinated(Habbo habbo, Achievement achievement, int amount, int queuedAmount)
            throws SQLException {
        int currentProgress = Math.max(0, habbo.getHabboStats().getAchievementProgress(achievement));
        int newProgress = (int) Math.min(Integer.MAX_VALUE, (long) currentProgress + amount);
        // One snapshot for the whole transition, so a reload cannot change the levels midway.
        Map<Integer, AchievementLevel> levels = achievement.levels();
        AchievementLevel oldLevel = Achievement.levelForProgress(levels, currentProgress);

        if (achievement.state == 0 || achievement.state == 2 || achievement.state == 3) return;
        if (hasAchieved(habbo, achievement) || currentProgress == newProgress) {
            if (queuedAmount > 0) {
                try (Connection connection = openConnection()) {
                    AchievementRewards.persist(
                            connection,
                            habbo.getHabboInfo().getId(),
                            achievement,
                            currentProgress,
                            currentProgress,
                            List.of(),
                            List.of(),
                            queuedAmount,
                            habbo.getHabboStats().getAchievementScore());
                }
            }
            return;
        }

        var plugins = Emulator.getPluginManager();
        if (plugins.isRegistered(UserAchievementProgressEvent.class, true)
                && plugins.fireEvent(new UserAchievementProgressEvent(habbo, achievement, amount))
                        .isCancelled()) return;

        AchievementLevel newLevel = Achievement.levelForProgress(levels, newProgress);
        List<AchievementLevel> earnedLevels = levels.values().stream()
                .filter(level -> (oldLevel == null || level.level > oldLevel.level)
                        && newLevel != null
                        && level.level <= newLevel.level)
                .sorted(java.util.Comparator.comparingInt(level -> level.level))
                .toList();

        if (!earnedLevels.isEmpty()
                && plugins.isRegistered(UserAchievementLeveledEvent.class, true)
                && plugins.fireEvent(new UserAchievementLeveledEvent(habbo, achievement, oldLevel, newLevel))
                        .isCancelled()) return;

        int userId = habbo.getHabboInfo().getId();
        List<EconomyOperation> rewards = new java.util.ArrayList<>();
        for (AchievementLevel level : earnedLevels) {
            if (level.rewardAmount <= 0) continue;

            int currency = level.rewardType;
            int reward;
            if (currency == EconomyLedger.CREDITS) {
                UserCreditsEvent event = new UserCreditsEvent(habbo, level.rewardAmount);
                if (plugins.fireEvent(event).isCancelled()) continue;
                reward = event.credits;
            } else {
                UserPointsEvent event = new UserPointsEvent(habbo, level.rewardAmount, currency);
                if (plugins.fireEvent(event).isCancelled()) continue;
                reward = event.points;
                currency = event.type;
            }
            if (reward == 0) continue;

            rewards.add(new EconomyOperation(
                    "achievement:" + userId + ":" + achievement.id + ":" + level.level,
                    userId,
                    userId,
                    "achievement_reward",
                    "achievement.level",
                    currency,
                    reward,
                    null,
                    achievement.name));
        }

        AchievementRewards.Result result;
        synchronized (habbo.getHabboStats()) {
            try (Connection connection = openConnection()) {
                result = AchievementRewards.persist(
                        connection,
                        userId,
                        achievement,
                        currentProgress,
                        newProgress,
                        earnedLevels,
                        rewards,
                        queuedAmount,
                        habbo.getHabboStats().getAchievementScore());
            }
            if (result.badge() != null) habbo.getHabboStats().achievementScore = result.score();
        }

        habbo.getHabboStats().setProgress(achievement, newProgress);
        for (int index = 0; index < rewards.size(); index++) {
            LedgerWalletMutation.applyCommitted(
                    habbo,
                    rewards.get(index).currencyType(),
                    result.balances().get(index).balanceAfter());
        }
        for (EconomyOperation reward : rewards) {
            if (reward.currencyType() == EconomyLedger.CREDITS)
                habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
            else if (reward.currencyType() == 0) habbo.getClient().sendResponse(new UserCurrencyComposer(habbo));
            else
                habbo.getClient()
                        .sendResponse(new UserPointsComposer(
                                habbo.getHabboInfo().getCurrencyAmount(reward.currencyType()),
                                reward.delta(),
                                reward.currencyType()));
        }

        if (result.badge() != null) {
            BadgesComponent badges = habbo.getInventory().getBadgesComponent();
            HabboBadge badge = null;
            for (HabboBadge owned : badges.getBadgesSnapshot()) {
                if (!isAchievementBadge(owned.getCode(), achievement.name)) continue;
                if (owned.getId() == result.badge().id()) badge = owned;
                else badges.removeBadge(owned);
            }
            if (badge == null) {
                badge = new HabboBadge(
                        result.badge().id(),
                        result.badge().code(),
                        result.badge().slot(),
                        habbo);
                badges.addBadge(badge);
            }
            badge.setCode(result.badge().code());
            badge.setSlot(result.badge().slot());
            badge.needsInsert(false);
            badge.needsUpdate(false);
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            habbo.getClient()
                    .sendResponse(
                            new AddHabboItemComposer(badge.getId(), AddHabboItemComposer.AddHabboItemCategory.BADGE));
            habbo.getClient().sendResponse(new UserAchievementScoreComposer(habbo));
            for (AchievementLevel level : earnedLevels) {
                habbo.getClient()
                        .sendResponse(new AchievementUnlockedComposer(habbo, achievement, level, badge.getId()));
            }

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                if (badge.getSlot() > 0) {
                    habbo.getHabboInfo()
                            .getCurrentRoom()
                            .sendComposer(new UserBadgesComposer(badges.getWearingBadges(), userId).compose());
                }
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
            }
        }

        habbo.getClient().sendResponse(new AchievementProgressComposer(habbo, achievement));
        if (TALENTTRACK_ENABLED) {
            AchievementManager manager = Emulator.getGameEnvironment().getAchievementManager();
            for (TalentTrackType type : TalentTrackType.values()) {
                if (manager.talentTrackLevels.containsKey(type))
                    manager.handleTalentTrackAchievement(habbo, type, achievement);
            }
        }
    }

    public static void processQueuedAchievements(Habbo habbo) {
        try {
            LedgerWalletMutation.coordinated(habbo, () -> {
                Map<Integer, Integer> queued = new LinkedHashMap<>();
                try (Connection connection = openConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT achievement_id, amount FROM users_achievements_queue WHERE user_id = ? AND amount > 0")) {
                    statement.setInt(1, habbo.getHabboInfo().getId());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) queued.put(result.getInt("achievement_id"), result.getInt("amount"));
                    }
                }
                AchievementManager manager = Emulator.getGameEnvironment().getAchievementManager();
                for (Map.Entry<Integer, Integer> entry : queued.entrySet()) {
                    Achievement achievement = manager.getAchievement(entry.getKey());
                    if (achievement != null)
                        progressWhileCoordinated(habbo, achievement, entry.getValue(), entry.getValue());
                }
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to apply queued achievements for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
        }
    }

    private static Connection openConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /**
     * True when the badge code belongs to this achievement's lineage: the "ACH_" prefix,
     * the achievement name (badge codes vary in case), then the level as trailing digits.
     * The digits requirement keeps prefix-sharing achievement names apart — "RoomEntry"
     * must not claim "ACH_RoomEntryFriend5".
     */
    static boolean isAchievementBadge(String badgeCode, String achievementName) {
        if (badgeCode == null || achievementName == null) return false;

        String prefix = "ACH_" + achievementName;

        if (badgeCode.length() <= prefix.length()) return false;

        if (!badgeCode.regionMatches(true, 0, prefix, 0, prefix.length())) return false;

        for (int i = prefix.length(); i < badgeCode.length(); i++) {
            if (!Character.isDigit(badgeCode.charAt(i))) return false;
        }

        return true;
    }

    public static boolean hasAchieved(Habbo habbo, Achievement achievement) {
        int currentProgress = habbo.getHabboStats().getAchievementProgress(achievement);

        if (currentProgress == -1) {
            return false;
        }

        Map<Integer, AchievementLevel> levels = achievement.levels();
        AchievementLevel level = Achievement.levelForProgress(levels, currentProgress);

        if (level == null) return false;

        AchievementLevel nextLevel = levels.get(level.level + 1);

        return nextLevel == null && currentProgress >= level.progress;
    }

    public static void createUserEntry(Habbo habbo, Achievement achievement) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_achievements (user_id, achievement_name, progress) VALUES (?, ?, ?)")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setString(2, achievement.name);
            statement.setInt(3, 0);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void saveAchievements(Habbo habbo) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_achievements SET progress = GREATEST(progress, ?) WHERE achievement_name = ? AND user_id = ? LIMIT 1")) {
            statement.setInt(3, habbo.getHabboInfo().getId());
            for (Map.Entry<Achievement, Integer> map :
                    habbo.getHabboStats().getAchievementProgress().entrySet()) {
                statement.setInt(1, map.getValue());
                statement.setString(2, map.getKey().name);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int getAchievementProgressForHabbo(int userId, Achievement achievement) {
        if (achievement == null) {
            return 0;
        }

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT progress FROM users_achievements WHERE user_id = ? AND achievement_name = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, achievement.name);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("progress");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }

    public void reload() {
        long millis = System.currentTimeMillis();
        synchronized (this.reloadLock) {
            try (Connection connection = openConnection()) {
                Map<String, Achievement> loaded;
                try (Statement statement = connection.createStatement();
                        ResultSet set = statement.executeQuery("SELECT * FROM achievements ORDER BY name, level")) {
                    loaded = readAchievements(set);
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                    loaded = null;
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                    loaded = null;
                }
                // A failed read keeps the current achievements instead of publishing a partial set.
                if (loaded != null) this.applyLoaded(loaded);

                synchronized (this.talentTrackLevels) {
                    this.talentTrackLevels.clear();

                    try (Statement statement = connection.createStatement();
                            ResultSet set =
                                    statement.executeQuery("SELECT * FROM achievements_talents ORDER BY level ASC")) {
                        while (set.next()) {
                            TalentTrackLevel level = new TalentTrackLevel(set);

                            if (!this.talentTrackLevels.containsKey(level.type)) {
                                this.talentTrackLevels.put(level.type, new LinkedHashMap<>());
                            }

                            this.talentTrackLevels.get(level.type).put(level.level, level);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                LOGGER.error("Achievement Manager -> Failed to load!");
                return;
            }
        }

        LOGGER.info("Achievement Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    /** Reads every achievement with its complete level set, without touching the live ones. */
    static Map<String, Achievement> readAchievements(ResultSet set) throws SQLException {
        Map<String, Achievement> loaded = new LinkedHashMap<>();
        while (set.next()) {
            String name = set.getString("name");
            Achievement achievement = loaded.get(name);
            if (achievement == null) {
                loaded.put(name, new Achievement(set));
            } else {
                achievement.addLevel(new AchievementLevel(set));
                achievement.loadMetadata(set);
            }
        }
        return loaded;
    }

    /**
     * Publishes a reloaded set: known achievements keep their identity (online progress is keyed by it)
     * and swap in their new levels at once, new ones are added and deleted ones removed.
     */
    void applyLoaded(Map<String, Achievement> loaded) {
        for (Achievement fresh : loaded.values()) {
            Achievement current = this.achievements.get(fresh.name);
            if (current != null) current.refreshFrom(fresh);
            else this.achievements.put(fresh.name, fresh);
        }
        this.achievements.keySet().retainAll(loaded.keySet());
    }

    public Achievement getAchievement(String name) {
        return this.achievements.get(name);
    }

    public Achievement getAchievement(int id) {
        for (Achievement achievement : this.achievements.values()) {
            if (achievement.id == id) {
                return achievement;
            }
        }

        return null;
    }

    public Map<String, Achievement> getAchievements() {
        return this.achievements;
    }

    public LinkedHashMap<Integer, TalentTrackLevel> getTalenTrackLevels(TalentTrackType type) {
        return this.talentTrackLevels.get(type);
    }

    public TalentTrackLevel calculateTalenTrackLevel(Habbo habbo, TalentTrackType type) {
        TalentTrackLevel level = null;

        for (Map.Entry<Integer, TalentTrackLevel> entry :
                this.talentTrackLevels.get(type).entrySet()) {
            final boolean[] allCompleted = {true};
            for (Map.Entry<Achievement, Integer> achievementEntry :
                    entry.getValue().achievements.entrySet()) {
                AchievementLevel requiredLevel =
                        achievementEntry.getKey().levels.get(achievementEntry.getValue());
                if (requiredLevel == null
                        || habbo.getHabboStats().getAchievementProgress(achievementEntry.getKey())
                                < requiredLevel.progress) {
                    allCompleted[0] = false;
                    break;
                }
            }

            if (allCompleted[0]) {
                if (level == null || level.level < entry.getValue().level) {
                    level = entry.getValue();
                }
            } else {
                break;
            }
        }

        return level;
    }

    public void handleTalentTrackAchievement(Habbo habbo, TalentTrackType type, Achievement achievement) {
        try {
            LedgerWalletMutation.coordinated(habbo, () -> {
                synchronized (habbo.getHabboStats()) {
                    TalentTrackLevel currentLevel = this.calculateTalenTrackLevel(habbo, type);
                    int previousLevel = habbo.getHabboStats().talentTrackLevel(type);
                    if (currentLevel == null || currentLevel.level <= previousLevel) return null;

                    List<TalentTrackLevel> earnedLevels = this.talentTrackLevels.get(type).values().stream()
                            .filter(level -> level.level > previousLevel && level.level <= currentLevel.level)
                            .sorted(java.util.Comparator.comparingInt(level -> level.level))
                            .toList();
                    int clubSeconds = CatalogPurchaseMath.checkedSubscriptionSeconds(earnedLevels.stream()
                            .mapToInt(level -> level.hcDays)
                            .reduce(0, Math::addExact));
                    if (clubSeconds > 0) {
                        Subscription subscription = habbo.getHabboStats().getSubscription(Subscription.HABBO_CLUB);
                        Event event = subscription == null
                                ? new UserSubscriptionCreatedEvent(
                                        habbo.getHabboInfo().getId(), Subscription.HABBO_CLUB, clubSeconds)
                                : new UserSubscriptionExtendedEvent(
                                        habbo.getHabboInfo().getId(), subscription, clubSeconds);
                        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return null;
                    }
                    var environment = Emulator.getGameEnvironment();
                    TalentTrackRewards.Result result;
                    try (Connection connection = openConnection()) {
                        result = TalentTrackRewards.persist(
                                connection,
                                habbo,
                                environment.getItemManager(),
                                environment.getSubscriptionManager(),
                                type,
                                previousLevel,
                                currentLevel.level,
                                earnedLevels,
                                Math.toIntExact(java.time.Instant.now().getEpochSecond()));
                    }
                    habbo.getHabboStats().setTalentLevel(type, currentLevel.level);
                    if (result.trade()) habbo.getHabboStats().perkTrade = true;
                    if (result.subscription() != null) {
                        Subscription subscription = habbo.getHabboStats().subscriptions.stream()
                                .filter(owned -> owned.getSubscriptionId()
                                        == result.subscription().getSubscriptionId())
                                .findFirst()
                                .orElse(null);
                        if (subscription != null) {
                            subscription.applyPersistedDuration(
                                    result.subscription().getDuration());
                            subscription.onExtended(result.clubSeconds());
                        } else {
                            habbo.getHabboStats().subscriptions.add(result.subscription());
                            result.subscription().onCreated();
                        }
                    }
                    for (HabboItem item : result.items()) {
                        habbo.getInventory().getItemsComponent().addItem(item);
                        habbo.getClient().sendResponse(new AddHabboItemComposer(item));
                    }
                    if (!result.items().isEmpty()) habbo.getClient().sendResponse(new InventoryRefreshComposer());
                    for (HabboBadge badge : result.badges()) {
                        habbo.getInventory().getBadgesComponent().addBadge(badge);
                        habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
                    }
                    for (TalentTrackLevel level : earnedLevels)
                        habbo.getClient().sendResponse(new TalentLevelUpdateComposer(type, level));

                    // Official TalentTrackLevel (1203): the toolbar / hotel-view promo tracks the pair
                    // (level, maxLevel), so refresh it whenever the level moves.
                    habbo.getClient().sendResponse(UserCitizinShipComposer.forHabbo(habbo, type));
                }
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to grant talent rewards for user {}",
                    habbo.getHabboInfo().getId(),
                    exception);
        }
    }

    public TalentTrackLevel getTalentTrackLevel(TalentTrackType type, int level) {
        return this.talentTrackLevels.get(type).get(level);
    }
}
