package polaris.hotel.achievements;

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
import polaris.Emulator;
import polaris.hotel.catalog.CatalogPurchaseMath;
import polaris.hotel.earnings.EarningsCategory;
import polaris.hotel.earnings.EarningsNotifications;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.messenger.FriendActivityNotifier;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.messages.outgoing.achievements.AchievementProgressComposer;
import polaris.messages.outgoing.achievements.AchievementUnlockedComposer;
import polaris.messages.outgoing.achievements.talenttrack.TalentLevelUpdateComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;
import polaris.messages.outgoing.users.CitizenshipVipOfferPromoEnabledComposer;
import polaris.messages.outgoing.users.UserAchievementScoreComposer;
import polaris.messages.outgoing.users.UserBadgesComposer;
import polaris.messages.outgoing.users.UserCitizinShipComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.Event;
import polaris.plugin.events.users.UserCreditsEvent;
import polaris.plugin.events.users.UserPointsEvent;
import polaris.plugin.events.users.achievements.UserAchievementLeveledEvent;
import polaris.plugin.events.users.achievements.UserAchievementProgressEvent;
import polaris.plugin.events.users.subscriptions.UserSubscriptionCreatedEvent;
import polaris.plugin.events.users.subscriptions.UserSubscriptionExtendedEvent;

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

    public static void progressAchievement(int userId, Achievement achievement) {
        progressAchievement(userId, achievement, 1);
    }

    public static void progressAchievement(int userId, Achievement achievement, int amount) {
        if (achievement != null && amount > 0 && userId > 0) {
            User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

            if (user != null) {
                progressAchievement(user, achievement, amount);
            } else {
                queueOffline(userId, achievement, amount);
            }
        }
    }

    /** Stores progress for a user that is not online; it is applied on their next login. */
    private static void queueOffline(int userId, Achievement achievement, int amount) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(""
                        + "INSERT INTO users_achievements_queue (user_id, achievement_id, amount) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE amount = LEAST(2147483647, CAST(amount AS SIGNED) + ?)")) {
            statement.setInt(1, userId);
            statement.setInt(2, achievement.id);
            statement.setInt(3, amount);
            statement.setInt(4, amount);
            statement.execute();
        } catch (SQLException | RuntimeException e) {
            LOGGER.error("Unable to queue achievement {} for user {}", achievement.name, userId, e);
        }
    }

    public static void progressAchievement(User user, Achievement achievement) {
        progressAchievement(user, achievement, 1);
    }

    public static void progressAchievement(User user, Achievement achievement, int amount) {
        if (achievement == null || user == null || amount <= 0) return;
        if (!user.isOnline()) {
            // Logging in or out: keep the progress for the next login instead of dropping it.
            if (user.getUserInfo() != null && user.getUserInfo().getId() > 0)
                queueOffline(user.getUserInfo().getId(), achievement, amount);
            return;
        }

        try {
            LedgerWalletMutation.coordinated(user, () -> {
                progressWhileCoordinated(user, achievement, amount, 0);
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to progress achievement {} for user {}",
                    achievement.name,
                    user.getUserInfo().getId(),
                    exception);
        }
    }

    private static void progressWhileCoordinated(User user, Achievement achievement, int amount, int queuedAmount)
            throws SQLException {
        int currentProgress = Math.max(0, user.getUserStats().getAchievementProgress(achievement));
        int newProgress = (int) Math.min(Integer.MAX_VALUE, (long) currentProgress + amount);
        // One snapshot for the whole transition, so a reload cannot change the levels midway.
        Map<Integer, AchievementLevel> levels = achievement.levels();
        AchievementLevel oldLevel = Achievement.levelForProgress(levels, currentProgress);

        if (achievement.state == 0 || achievement.state == 2 || achievement.state == 3) return;
        if (hasAchieved(user, achievement) || currentProgress == newProgress) {
            if (queuedAmount > 0) {
                try (Connection connection = openConnection()) {
                    AchievementRewards.persist(
                            connection,
                            user.getUserInfo().getId(),
                            achievement,
                            currentProgress,
                            currentProgress,
                            List.of(),
                            List.of(),
                            queuedAmount,
                            user.getUserStats().getAchievementScore());
                }
            }
            return;
        }

        var plugins = Emulator.getPluginManager();
        if (plugins.isRegistered(UserAchievementProgressEvent.class, true)
                && plugins.fireEvent(new UserAchievementProgressEvent(user, achievement, amount))
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
                && plugins.fireEvent(new UserAchievementLeveledEvent(user, achievement, oldLevel, newLevel))
                        .isCancelled()) return;

        int userId = user.getUserInfo().getId();
        List<EconomyOperation> rewards = new java.util.ArrayList<>();
        for (AchievementLevel level : earnedLevels) {
            if (level.rewardAmount <= 0) continue;

            int currency = level.rewardType;
            int reward;
            if (currency == EconomyLedger.CREDITS) {
                UserCreditsEvent event = new UserCreditsEvent(user, level.rewardAmount);
                if (plugins.fireEvent(event).isCancelled()) continue;
                reward = event.credits;
            } else {
                UserPointsEvent event = new UserPointsEvent(user, level.rewardAmount, currency);
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

        String achievementsPeriod = EarningsNotifications.period(user, EarningsCategory.ACHIEVEMENTS);
        AchievementRewards.Result result;
        synchronized (user.getUserStats()) {
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
                        user.getUserStats().getAchievementScore());
            }
            if (result.badge() != null) user.getUserStats().achievementScore = result.score();
        }
        EarningsNotifications.afterProgress(user, EarningsCategory.ACHIEVEMENTS, achievementsPeriod);

        user.getUserStats().setProgress(achievement, newProgress);
        for (int index = 0; index < rewards.size(); index++) {
            LedgerWalletMutation.applyCommitted(
                    user,
                    rewards.get(index).currencyType(),
                    result.balances().get(index).balanceAfter());
        }
        for (EconomyOperation reward : rewards) {
            if (reward.currencyType() == EconomyLedger.CREDITS)
                user.getClient().sendResponse(new UserCreditsComposer(user));
            else if (reward.currencyType() == 0) user.getClient().sendResponse(new UserCurrencyComposer(user));
            else
                user.getClient()
                        .sendResponse(new UserPointsComposer(
                                user.getUserInfo().getCurrencyAmount(reward.currencyType()),
                                reward.delta(),
                                reward.currencyType()));
        }

        if (result.badge() != null) {
            BadgesComponent badges = user.getInventory().getBadgesComponent();
            UserBadge badge = null;
            for (UserBadge owned : badges.getBadgesSnapshot()) {
                if (!isAchievementBadge(owned.getCode(), achievement.name)) continue;
                if (owned.getId() == result.badge().id()) badge = owned;
                else badges.removeBadge(owned);
            }
            if (badge == null) {
                badge = new UserBadge(
                        result.badge().id(),
                        result.badge().code(),
                        result.badge().slot(),
                        user);
                badges.addBadge(badge);
            }
            badge.setCode(result.badge().code());
            badge.setSlot(result.badge().slot());
            badge.needsInsert(false);
            badge.needsUpdate(false);
            user.getClient().sendResponse(new AddUserBadgeComposer(badge));
            user.getClient()
                    .sendResponse(
                            new AddUserItemComposer(badge.getId(), AddUserItemComposer.AddUserItemCategory.BADGE));
            user.getClient().sendResponse(new UserAchievementScoreComposer(user));
            for (AchievementLevel level : earnedLevels) {
                user.getClient().sendResponse(new AchievementUnlockedComposer(user, achievement, level, badge.getId()));
            }
            if (!earnedLevels.isEmpty()) FriendActivityNotifier.achievementUnlocked(user, badge.getCode());

            if (user.getUserInfo().getCurrentRoom() != null) {
                if (badge.getSlot() > 0) {
                    user.getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new UserBadgesComposer(badges.getWearingBadges(), userId).compose());
                }
                user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
            }
        }

        user.getClient().sendResponse(new AchievementProgressComposer(user, achievement));
        if (TALENTTRACK_ENABLED) {
            AchievementManager manager = Emulator.getGameEnvironment().getAchievementManager();
            for (TalentTrackType type : TalentTrackType.values()) {
                if (manager.talentTrackLevels.containsKey(type))
                    manager.handleTalentTrackAchievement(user, type, achievement);
            }
        }
    }

    public static void processQueuedAchievements(User user) {
        try {
            LedgerWalletMutation.coordinated(user, () -> {
                Map<Integer, Integer> queued = new LinkedHashMap<>();
                try (Connection connection = openConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT achievement_id, amount FROM users_achievements_queue WHERE user_id = ? AND amount > 0")) {
                    statement.setInt(1, user.getUserInfo().getId());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) queued.put(result.getInt("achievement_id"), result.getInt("amount"));
                    }
                }
                AchievementManager manager = Emulator.getGameEnvironment().getAchievementManager();
                for (Map.Entry<Integer, Integer> entry : queued.entrySet()) {
                    Achievement achievement = manager.getAchievement(entry.getKey());
                    if (achievement != null)
                        progressWhileCoordinated(user, achievement, entry.getValue(), entry.getValue());
                }
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to apply queued achievements for user {}",
                    user.getUserInfo().getId(),
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

    public static boolean hasAchieved(User user, Achievement achievement) {
        int currentProgress = user.getUserStats().getAchievementProgress(achievement);

        if (currentProgress == -1) {
            return false;
        }

        Map<Integer, AchievementLevel> levels = achievement.levels();
        AchievementLevel level = Achievement.levelForProgress(levels, currentProgress);

        if (level == null) return false;

        AchievementLevel nextLevel = levels.get(level.level + 1);

        return nextLevel == null && currentProgress >= level.progress;
    }

    public static void createUserEntry(User user, Achievement achievement) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_achievements (user_id, achievement_name, progress) VALUES (?, ?, ?)")) {
            statement.setInt(1, user.getUserInfo().getId());
            statement.setString(2, achievement.name);
            statement.setInt(3, 0);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void saveAchievements(User user) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_achievements SET progress = GREATEST(progress, ?) WHERE achievement_name = ? AND user_id = ? LIMIT 1")) {
            statement.setInt(3, user.getUserInfo().getId());
            for (Map.Entry<Achievement, Integer> map :
                    user.getUserStats().getAchievementProgress().entrySet()) {
                statement.setInt(1, map.getValue());
                statement.setString(2, map.getKey().name);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int getAchievementProgressForUser(int userId, Achievement achievement) {
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

    public TalentTrackLevel calculateTalenTrackLevel(User user, TalentTrackType type) {
        TalentTrackLevel level = null;

        for (Map.Entry<Integer, TalentTrackLevel> entry :
                this.talentTrackLevels.get(type).entrySet()) {
            // A level whose tasks are not all known is never completed (it used to pass with no tasks at all).
            final boolean[] allCompleted = {!entry.getValue().missingAchievements};
            for (Map.Entry<Achievement, Integer> achievementEntry :
                    entry.getValue().achievements.entrySet()) {
                AchievementLevel requiredLevel =
                        achievementEntry.getKey().levels.get(achievementEntry.getValue());
                if (requiredLevel == null
                        || user.getUserStats().getAchievementProgress(achievementEntry.getKey())
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

    public void handleTalentTrackAchievement(User user, TalentTrackType type, Achievement achievement) {
        try {
            LedgerWalletMutation.coordinated(user, () -> {
                synchronized (user.getUserStats()) {
                    TalentTrackLevel currentLevel = this.calculateTalenTrackLevel(user, type);
                    int previousLevel = user.getUserStats().talentTrackLevel(type);
                    if (currentLevel == null || currentLevel.level <= previousLevel) return null;

                    List<TalentTrackLevel> earnedLevels = this.talentTrackLevels.get(type).values().stream()
                            .filter(level -> level.level > previousLevel && level.level <= currentLevel.level)
                            .sorted(java.util.Comparator.comparingInt(level -> level.level))
                            .toList();
                    int clubSeconds = CatalogPurchaseMath.checkedSubscriptionSeconds(earnedLevels.stream()
                            .mapToInt(level -> level.hcDays)
                            .reduce(0, Math::addExact));
                    if (clubSeconds > 0) {
                        Subscription subscription = user.getUserStats().getSubscription(Subscription.CLUB);
                        Event event = subscription == null
                                ? new UserSubscriptionCreatedEvent(
                                        user.getUserInfo().getId(), Subscription.CLUB, clubSeconds)
                                : new UserSubscriptionExtendedEvent(
                                        user.getUserInfo().getId(), subscription, clubSeconds);
                        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return null;
                    }
                    var environment = Emulator.getGameEnvironment();
                    TalentTrackRewards.Result result;
                    try (Connection connection = openConnection()) {
                        result = TalentTrackRewards.persist(
                                connection,
                                user,
                                environment.getItemManager(),
                                environment.getSubscriptionManager(),
                                type,
                                previousLevel,
                                currentLevel.level,
                                earnedLevels,
                                Math.toIntExact(java.time.Instant.now().getEpochSecond()));
                    }
                    String levelPeriod = EarningsNotifications.period(user, EarningsCategory.LEVEL_PROGRESS);
                    user.getUserStats().setTalentLevel(type, currentLevel.level);
                    EarningsNotifications.afterProgress(user, EarningsCategory.LEVEL_PROGRESS, levelPeriod);
                    if (result.trade()) user.getUserStats().perkTrade = true;
                    if (result.subscription() != null) {
                        Subscription subscription = user.getUserStats().subscriptions.stream()
                                .filter(owned -> owned.getSubscriptionId()
                                        == result.subscription().getSubscriptionId())
                                .findFirst()
                                .orElse(null);
                        if (subscription != null) {
                            subscription.applyPersistedDuration(
                                    result.subscription().getDuration());
                            subscription.onExtended(result.clubSeconds());
                        } else {
                            user.getUserStats().subscriptions.add(result.subscription());
                            result.subscription().onCreated();
                        }
                    }
                    for (UserItem item : result.items()) {
                        user.getInventory().getItemsComponent().addItem(item);
                        user.getClient().sendResponse(new AddUserItemComposer(item));
                    }
                    if (!result.items().isEmpty()) user.getClient().sendResponse(new InventoryRefreshComposer());
                    for (UserBadge badge : result.badges()) {
                        user.getInventory().getBadgesComponent().addBadge(badge);
                        user.getClient().sendResponse(new AddUserBadgeComposer(badge));
                    }
                    for (TalentTrackLevel level : earnedLevels)
                        user.getClient().sendResponse(new TalentLevelUpdateComposer(type, level));

                    // Official TalentTrackLevel (1203): the toolbar / hotel-view promo tracks the pair
                    // (level, maxLevel), so refresh it whenever the level moves.
                    user.getClient().sendResponse(UserCitizinShipComposer.forUser(user, type));
                    if (type == TalentTrackType.CITIZENSHIP && result.clubSeconds() > 0) {
                        user.getClient().sendResponse(new CitizenshipVipOfferPromoEnabledComposer());
                    }
                }
                return null;
            });
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error(
                    "Unable to grant talent rewards for user {}",
                    user.getUserInfo().getId(),
                    exception);
        }
    }

    public TalentTrackLevel getTalentTrackLevel(TalentTrackType type, int level) {
        return this.talentTrackLevels.get(type).get(level);
    }
}
