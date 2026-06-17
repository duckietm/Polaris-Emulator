package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardClaimed;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.cache.HabboOfferPurchase;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.plugin.events.users.subscriptions.UserSubscriptionCreatedEvent;
import com.eu.habbo.plugin.events.users.subscriptions.UserSubscriptionExtendedEvent;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import gnu.trove.stack.array.TIntArrayStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HabboStats implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboStats.class);

    public final TIntArrayList secretRecipes;
    public final HabboNavigatorWindowSettings navigatorWindowSettings;
    public final THashMap<String, Object> cache;
    public final ArrayList<CalendarRewardClaimed> calendarRewardsClaimed;
    public final TIntObjectMap<HabboOfferPurchase> offerCache = new TIntObjectHashMap<>();
    private final AtomicInteger lastOnlineTime = new AtomicInteger(Emulator.getIntUnixTimestamp());
    private final THashMap<Achievement, Integer> achievementProgress;
    private final THashMap<Achievement, Integer> achievementCache;
    private final THashMap<Integer, CatalogItem> recentPurchases;
    private final TIntArrayList favoriteRooms;
    private final TIntArrayList ignoredUsers;
    private TIntArrayList roomsVists;
    public int achievementScore;
    public int respectPointsReceived;
    public int respectPointsGiven;
    public int respectPointsToGive;
    public int petRespectPointsToGive;
    public boolean blockFollowing;
    public boolean blockFriendRequests;
    public boolean blockRoomInvites;
    public boolean blockStaffAlerts;
    public boolean preferOldChat;
    public boolean blockCameraFollow;
    public RoomChatMessageBubbles chatColor;
    public int volumeSystem;
    public int volumeFurni;
    public int volumeTrax;
    public int guild;
    public List<Integer> guilds;
    public String[] tags;
    public TIntArrayStack votedRooms;
    public int loginStreak;
    public int rentedItemId;
    public int rentedTimeEnd;
    public int hofPoints;
    public boolean ignorePets;
    public boolean ignoreBots;
    public int citizenshipLevel;
    public int helpersLevel;
    public boolean perkTrade;
    public long roomEnterTimestamp;
    public AtomicInteger chatCounter = new AtomicInteger(0);
    public long lastChat;
    public long lastUsersSearched;
    public boolean nux;
    public boolean nuxReward;
    public int nuxStep = 1;
    public int mutedCount = 0;
    public boolean mutedBubbleTracker = false;
    public String changeNameChecked = "";
    public boolean allowNameChange;
    public boolean isPurchasingFurniture = false;
    public int forumPostsCount;
    public THashMap<Integer, List<Integer>> ltdPurchaseLog = new THashMap<>(0);
    public long lastTradeTimestamp = Emulator.getIntUnixTimestamp();
    public long lastGiftTimestamp = Emulator.getIntUnixTimestamp();
    public long lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();
    public long lastFloorplanSaveTimestamp = 0;
    public int uiFlags;
    public boolean hasGottenDefaultSavedSearches;
    private HabboInfo habboInfo;
    private boolean allowTrade;
    private boolean mentionsEnabled;
    private boolean massMentionsEnabled;
    private int clubExpireTimestamp;
    private int muteEndTime;
    public int maxFriends;
    public int maxRooms;
    public int lastHCPayday;
    public int hcGiftsClaimed;
    public int buildersClubBonusFurni;
    public int hcMessageLastModified = Emulator.getIntUnixTimestamp();
    public THashSet<Subscription> subscriptions;

    private HabboStats(ResultSet set, HabboInfo habboInfo) throws SQLException {
        this.cache = new THashMap<>(1000);
        this.achievementProgress = new THashMap<>(0);
        this.achievementCache = new THashMap<>(0);
        this.recentPurchases = new THashMap<>(0);
        this.favoriteRooms = new TIntArrayList(0);
        this.ignoredUsers = new TIntArrayList(0);
        this.roomsVists = new TIntArrayList(0);
        this.secretRecipes = new TIntArrayList(0);
        this.calendarRewardsClaimed = new ArrayList<>();

        this.habboInfo = habboInfo;

        this.achievementScore = set.getInt("achievement_score");
        this.respectPointsReceived = set.getInt("respects_received");
        this.respectPointsGiven = set.getInt("respects_given");
        this.petRespectPointsToGive = set.getInt("daily_pet_respect_points");
        this.respectPointsToGive = set.getInt("daily_respect_points");
        this.blockFollowing = set.getString("block_following").equals("1");
        this.blockFriendRequests = set.getString("block_friendrequests").equals("1");
        this.blockRoomInvites = set.getString("block_roominvites").equals("1");
        this.preferOldChat = set.getString("old_chat").equals("1");
        this.blockCameraFollow = set.getString("block_camera_follow").equals("1");
        this.guild = set.getInt("guild_id");
        this.guilds = new ArrayList<>();
        this.tags = set.getString("tags").split(";");
        this.allowTrade = set.getString("can_trade").equals("1");
        this.mentionsEnabled = "1".equals(safeColumnString(set, "mentions_enabled", "1"));
        this.massMentionsEnabled = "1".equals(safeColumnString(set, "mass_mentions_enabled", "1"));
        this.votedRooms = new TIntArrayStack();
        this.clubExpireTimestamp = set.getInt("club_expire_timestamp");
        this.loginStreak = set.getInt("login_streak");
        this.rentedItemId = set.getInt("rent_space_id");
        this.rentedTimeEnd = set.getInt("rent_space_endtime");
        this.volumeSystem = set.getInt("volume_system");
        this.volumeFurni = set.getInt("volume_furni");
        this.volumeTrax = set.getInt("volume_trax");
        this.chatColor = RoomChatMessageBubbles.getBubble(set.getInt("chat_color"));
        this.hofPoints = set.getInt("hof_points");
        this.blockStaffAlerts = set.getString("block_alerts").equals("1");
        this.citizenshipLevel = set.getInt("talent_track_citizenship_level");
        this.helpersLevel = set.getInt("talent_track_helpers_level");
        this.ignoreBots = set.getString("ignore_bots").equalsIgnoreCase("1");
        this.ignorePets = set.getString("ignore_pets").equalsIgnoreCase("1");
        this.nux = set.getString("nux").equals("1");
        this.muteEndTime = set.getInt("mute_end_timestamp");
        this.allowNameChange = set.getString("allow_name_change").equalsIgnoreCase("1");
        this.perkTrade = set.getString("perk_trade").equalsIgnoreCase("1");
        this.forumPostsCount = set.getInt("forums_post_count");
        this.uiFlags = set.getInt("ui_flags");
        this.hasGottenDefaultSavedSearches = set.getInt("has_gotten_default_saved_searches") == 1;
        this.maxFriends = set.getInt("max_friends");
        this.maxRooms = set.getInt("max_rooms");
        this.lastHCPayday = set.getInt("last_hc_payday");
        this.hcGiftsClaimed = set.getInt("hc_gifts_claimed");
        this.buildersClubBonusFurni = set.getInt("builders_club_bonus_furni");

        this.nuxReward = this.nux;

        this.subscriptions = Emulator.getGameEnvironment().getSubscriptionManager().getSubscriptionsForUser(this.habboInfo.getId());

        try (PreparedStatement statement = set.getStatement().getConnection().prepareStatement("SELECT * FROM user_window_settings WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, this.habboInfo.getId());
            try (ResultSet nSet = statement.executeQuery()) {
                if (nSet.next()) {
                    this.navigatorWindowSettings = new HabboNavigatorWindowSettings(nSet);
                } else {
                    try (PreparedStatement stmt = statement.getConnection().prepareStatement("INSERT INTO user_window_settings (user_id) VALUES (?)")) {
                        stmt.setInt(1, this.habboInfo.getId());
                        stmt.executeUpdate();
                    }

                    this.navigatorWindowSettings = new HabboNavigatorWindowSettings(habboInfo.getId());
                }
            }
        }

        try (PreparedStatement statement = set.getStatement().getConnection().prepareStatement("SELECT * FROM users_navigator_settings WHERE user_id = ?")) {
            statement.setInt(1, this.habboInfo.getId());
            try (ResultSet nSet = statement.executeQuery()) {
                while (nSet.next()) {
                    this.navigatorWindowSettings.addDisplayMode(nSet.getString("caption"), new HabboNavigatorPersonalDisplayMode(nSet));
                }
            }
        }

        try (PreparedStatement favoriteRoomsStatement = set.getStatement().getConnection().prepareStatement("SELECT * FROM users_favorite_rooms WHERE user_id = ?")) {
            favoriteRoomsStatement.setInt(1, this.habboInfo.getId());
            try (ResultSet favoriteSet = favoriteRoomsStatement.executeQuery()) {
                while (favoriteSet.next()) {
                    this.favoriteRooms.add(favoriteSet.getInt("room_id"));
                }
            }

        }

        try (PreparedStatement recipesStatement = set.getStatement().getConnection().prepareStatement("SELECT * FROM users_recipes WHERE user_id = ?")) {
            recipesStatement.setInt(1, this.habboInfo.getId());
            try (ResultSet recipeSet = recipesStatement.executeQuery()) {
                while (recipeSet.next()) {
                    this.secretRecipes.add(recipeSet.getInt("recipe"));
                }
            }
        }

        try (PreparedStatement calendarRewardsStatement = set.getStatement().getConnection().prepareStatement("SELECT * FROM calendar_rewards_claimed WHERE user_id = ?")) {
            calendarRewardsStatement.setInt(1, this.habboInfo.getId());
            try (ResultSet rewardSet = calendarRewardsStatement.executeQuery()) {
                while (rewardSet.next()) {
                    this.calendarRewardsClaimed.add(new CalendarRewardClaimed(rewardSet));
                }
            }
        }

        try (PreparedStatement ltdPurchaseLogStatement = set.getStatement().getConnection().prepareStatement("SELECT catalog_item_id, timestamp FROM catalog_items_limited WHERE user_id = ? AND timestamp > ?")) {
            ltdPurchaseLogStatement.setInt(1, this.habboInfo.getId());
            ltdPurchaseLogStatement.setInt(2, Emulator.getIntUnixTimestamp() - 86400);
            try (ResultSet ltdSet = ltdPurchaseLogStatement.executeQuery()) {
                while (ltdSet.next()) {
                    this.addLtdLog(ltdSet.getInt("catalog_item_id"), ltdSet.getInt("timestamp"));
                }
            }
        }

        try (PreparedStatement ignoredPlayersStatement = set.getStatement().getConnection().prepareStatement("SELECT target_id FROM users_ignored WHERE user_id = ?")) {
            ignoredPlayersStatement.setInt(1, this.habboInfo.getId());
            try (ResultSet ignoredSet = ignoredPlayersStatement.executeQuery()) {
                while (ignoredSet.next()) {
                    this.ignoredUsers.add(ignoredSet.getInt(1));
                }
            }
        }

        try (PreparedStatement loadOfferPurchaseStatement = set.getStatement().getConnection().prepareStatement("SELECT * FROM users_target_offer_purchases WHERE user_id = ?")) {
            loadOfferPurchaseStatement.setInt(1, this.habboInfo.getId());
            try (ResultSet offerSet = loadOfferPurchaseStatement.executeQuery()) {
                while (offerSet.next()) {
                    this.offerCache.put(offerSet.getInt("offer_id"), new HabboOfferPurchase(offerSet));
                }
            }
        }

        try (PreparedStatement loadRoomsVisit = set.getStatement().getConnection().prepareStatement("SELECT DISTINCT room_id FROM room_enter_log WHERE user_id = ?")) {
            loadRoomsVisit.setInt(1, this.habboInfo.getId());
            try (ResultSet roomSet = loadRoomsVisit.executeQuery()) {
                while (roomSet.next()) {
                    this.roomsVists.add(roomSet.getInt("room_id"));
                }
            }
        }
    }

    private static HabboStats createNewStats(HabboInfo habboInfo) {
        habboInfo.firstVisit = true;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_settings (user_id) VALUES (?)")) {
            statement.setInt(1, habboInfo.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return load(habboInfo);
    }

    public static HabboStats load(HabboInfo habboInfo) {
        HabboStats stats = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users_settings WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, habboInfo.getId());
                try (ResultSet set = statement.executeQuery()) {
                    set.next();
                    if (set.getRow() != 0) {
                        stats = new HabboStats(set, habboInfo);
                    } else {
                        stats = createNewStats(habboInfo);
                    }
                }
            }

            if (stats != null) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT guild_id FROM guilds_members WHERE user_id = ? AND level_id < 3 LIMIT 100")) {
                    statement.setInt(1, habboInfo.getId());
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            stats.guilds.add(set.getInt("guild_id"));
                        }
                    }
                }

                Collections.sort(stats.guilds);

                try (PreparedStatement statement = connection.prepareStatement("SELECT room_id FROM room_votes WHERE user_id = ?")) {
                    statement.setInt(1, habboInfo.getId());
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            stats.votedRooms.push(set.getInt("room_id"));
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users_achievements WHERE user_id = ?")) {
                    statement.setInt(1, habboInfo.getId());
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            Achievement achievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement(set.getString("achievement_name"));

                            if (achievement != null) {
                                stats.achievementProgress.put(achievement, set.getInt("progress"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return stats;
    }

    @Override
    public void run() {
        // Find difference between last sync and update with a new timestamp.
        int onlineTimeLast = this.lastOnlineTime.getAndUpdate(operand -> Emulator.getIntUnixTimestamp());
        int onlineTime = Emulator.getIntUnixTimestamp() - onlineTimeLast;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET achievement_score = ?, respects_received = ?, respects_given = ?, daily_respect_points = ?, block_following = ?, block_friendrequests = ?, online_time = online_time + ?, guild_id = ?, daily_pet_respect_points = ?, club_expire_timestamp = ?, login_streak = ?, rent_space_id = ?, rent_space_endtime = ?, volume_system = ?, volume_furni = ?, volume_trax = ?, block_roominvites = ?, old_chat = ?, block_camera_follow = ?, chat_color = ?, hof_points = ?, block_alerts = ?, talent_track_citizenship_level = ?, talent_track_helpers_level = ?, ignore_bots = ?, ignore_pets = ?, nux = ?, mute_end_timestamp = ?, allow_name_change = ?, perk_trade = ?, can_trade = ?, `forums_post_count` = ?, ui_flags = ?, has_gotten_default_saved_searches = ?, max_friends = ?, max_rooms = ?, last_hc_payday = ?, hc_gifts_claimed = ?, builders_club_bonus_furni = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.achievementScore);
                statement.setInt(2, this.respectPointsReceived);
                statement.setInt(3, this.respectPointsGiven);
                statement.setInt(4, this.respectPointsToGive);
                statement.setString(5, this.blockFollowing ? "1" : "0");
                statement.setString(6, this.blockFriendRequests ? "1" : "0");
                statement.setInt(7, onlineTime);
                statement.setInt(8, this.guild);
                statement.setInt(9, this.petRespectPointsToGive);
                statement.setInt(10, this.clubExpireTimestamp);
                statement.setInt(11, this.loginStreak);
                statement.setInt(12, this.rentedItemId);
                statement.setInt(13, this.rentedTimeEnd);
                statement.setInt(14, this.volumeSystem);
                statement.setInt(15, this.volumeFurni);
                statement.setInt(16, this.volumeTrax);
                statement.setString(17, this.blockRoomInvites ? "1" : "0");
                statement.setString(18, this.preferOldChat ? "1" : "0");
                statement.setString(19, this.blockCameraFollow ? "1" : "0");
                statement.setInt(20, this.chatColor.getType());
                statement.setInt(21, this.hofPoints);
                statement.setString(22, this.blockStaffAlerts ? "1" : "0");
                statement.setInt(23, this.citizenshipLevel);
                statement.setInt(24, this.helpersLevel);
                statement.setString(25, this.ignoreBots ? "1" : "0");
                statement.setString(26, this.ignorePets ? "1" : "0");
                statement.setString(27, this.nux ? "1" : "0");
                statement.setInt(28, this.muteEndTime);
                statement.setString(29, this.allowNameChange ? "1" : "0");
                statement.setString(30, this.perkTrade ? "1" : "0");
                statement.setString(31, this.allowTrade ? "1" : "0");
                statement.setInt(32, this.forumPostsCount);
                statement.setInt(33, this.uiFlags);
                statement.setInt(34, this.hasGottenDefaultSavedSearches ? 1 : 0);
                statement.setInt(35, this.maxFriends);
                statement.setInt(36, this.maxRooms);
                statement.setInt(37, this.lastHCPayday);
                statement.setInt(38, this.hcGiftsClaimed);
                statement.setInt(39, this.buildersClubBonusFurni);
                statement.setInt(40, this.habboInfo.getId());
                
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("UPDATE user_window_settings SET x = ?, y = ?, width = ?, height = ?, open_searches = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.navigatorWindowSettings.x);
                statement.setInt(2, this.navigatorWindowSettings.y);
                statement.setInt(3, this.navigatorWindowSettings.width);
                statement.setInt(4, this.navigatorWindowSettings.height);
                statement.setString(5, this.navigatorWindowSettings.openSearches ? "1" : "0");
                statement.setInt(6, this.habboInfo.getId());
                statement.executeUpdate();
            }

            if (!this.offerCache.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE users_target_offer_purchases SET state = ?, amount = ?, last_purchase = ? WHERE user_id = ? AND offer_id = ?")) {
                    for (HabboOfferPurchase purchase : this.offerCache.valueCollection()) {
                        if (!purchase.needsUpdate()) continue;

                        statement.setInt(1, purchase.getState());
                        statement.setInt(2, purchase.getAmount());
                        statement.setInt(3, purchase.getLastPurchaseTimestamp());
                        statement.setInt(4, this.habboInfo.getId());
                        statement.setInt(5, purchase.getOfferId());
                        statement.execute();
                    }
                }
            }

            this.navigatorWindowSettings.save(connection);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void dispose() {
        this.run();
        this.habboInfo = null;
        this.recentPurchases.clear();
    }

    public void addGuild(int guildId) {
        if (!this.guilds.contains(guildId)) {
            this.guilds.add(guildId);
        }
    }

    public void removeGuild(int guildId) {
        this.guilds.remove((Integer) guildId);
    }

    public boolean hasGuild(int guildId) {
        for (int i : this.guilds) {
            if (i == guildId)
                return true;
        }

        return false;
    }

    public int getAchievementScore() {
        return this.achievementScore;
    }

    public void addAchievementScore(int achievementScore) {
        this.achievementScore += achievementScore;
    }

    public int getAchievementProgress(Achievement achievement) {
        if (achievement == null) {
            return 0;
        }

        synchronized (this.achievementProgress) {
            Integer progress = this.achievementProgress.get(achievement);
            return progress != null ? progress : -1;
        }
    }

    public void setProgress(Achievement achievement, int progress) {
        synchronized (this.achievementProgress) {
            this.achievementProgress.put(achievement, progress);
        }
    }

    /** Atomic read-add-write so concurrent progress sources don't lose updates. Returns the new total. */
    public int incrementProgress(Achievement achievement, int amount) {
        synchronized (this.achievementProgress) {
            Integer current = this.achievementProgress.get(achievement);
            int next = (current != null ? current : 0) + amount;
            this.achievementProgress.put(achievement, next);
            return next;
        }
    }

    public int getRentedTimeEnd() {
        return this.rentedTimeEnd;
    }

    public void setRentedTimeEnd(int rentedTimeEnd) {
        this.rentedTimeEnd = rentedTimeEnd;
    }

    public int getRentedItemId() {
        return this.rentedItemId;
    }

    public void setRentedItemId(int rentedItemId) {
        this.rentedItemId = rentedItemId;
    }

    public boolean isRentingSpace() {
        return this.rentedTimeEnd >= Emulator.getIntUnixTimestamp();
    }

    public Subscription getSubscription(String subscriptionType) {
        for(Subscription subscription : subscriptions) {
            if(subscription.getSubscriptionType().equalsIgnoreCase(subscriptionType) && subscription.isActive() && subscription.getRemaining() > 0) {
                return subscription;
            }
        }
        return null;
    }

    public boolean hasSubscription(String subscriptionType) {
        Subscription subscription = getSubscription(subscriptionType);
        return subscription != null;
    }

    public int getSubscriptionExpireTimestamp(String subscriptionType) {
        Subscription subscription = getSubscription(subscriptionType);

        if(subscription == null)
            return 0;

        return subscription.getTimestampEnd();
    }

    public Subscription createSubscription(String subscriptionType, int duration) {
        Subscription subscription = getSubscription(subscriptionType);

        if(subscription != null) {
            if (!Emulator.getPluginManager().fireEvent(new UserSubscriptionExtendedEvent(this.habboInfo.getId(), subscription, duration)).isCancelled()) {
                subscription.addDuration(duration);
                subscription.onExtended(duration);
            }
            return subscription;
        }

        if (!Emulator.getPluginManager().fireEvent(new UserSubscriptionCreatedEvent(this.habboInfo.getId(), subscriptionType, duration)).isCancelled()) {
            int startTimestamp = Emulator.getIntUnixTimestamp();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO `users_subscriptions` (`user_id`, `subscription_type`, `timestamp_start`, `duration`, `active`) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, this.habboInfo.getId());
                statement.setString(2, subscriptionType);
                statement.setInt(3, startTimestamp);
                statement.setInt(4, duration);
                statement.setInt(5, 1);
                statement.execute();
                try (ResultSet set = statement.getGeneratedKeys()) {
                    if (set.next()) {
                        Class<? extends Subscription> subClazz = Emulator.getGameEnvironment().getSubscriptionManager().getSubscriptionClass(subscriptionType);
                        try {
                            Constructor<? extends Subscription> c = subClazz.getConstructor(Integer.class, Integer.class, String.class, Integer.class, Integer.class, Boolean.class);
                            c.setAccessible(true);
                            Subscription sub = c.newInstance(set.getInt(1), this.habboInfo.getId(), subscriptionType, startTimestamp, duration, true);
                            this.subscriptions.add(sub);
                            sub.onCreated();
                            return sub;
                        }
                        catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        return null;
    }

    public int getClubExpireTimestamp() {
        return getSubscriptionExpireTimestamp(Subscription.HABBO_CLUB);
    }

    public void setClubExpireTimestamp(int clubExpireTimestamp) {
        Subscription subscription = getSubscription(Subscription.HABBO_CLUB);
        int duration = clubExpireTimestamp - Emulator.getIntUnixTimestamp();

        if(subscription != null) {
            duration = clubExpireTimestamp - subscription.getTimestampStart();
        }

        if(duration > 0) {
            createSubscription(Subscription.HABBO_CLUB, duration);
        }
    }

    public boolean hasActiveClub() {
        return hasSubscription(Subscription.HABBO_CLUB);
    }

    public int getPastTimeAsClub() {
        int pastTimeAsHC = 0;
        for(Subscription subs : this.subscriptions) {
            if(subs.getSubscriptionType().equalsIgnoreCase(Subscription.HABBO_CLUB)) {
                pastTimeAsHC += subs.getDuration() - (Math.max(subs.getRemaining(), 0));
            }
        }
        return pastTimeAsHC;
    }

    public int getTimeTillNextClubGift() {
        int pastTimeAsClub = getPastTimeAsClub();
        int totalGifts = (int)Math.ceil(pastTimeAsClub / 2678400.0);
        return (totalGifts * 2678400) - pastTimeAsClub;
    }

    public int getRemainingClubGifts() {
        int totalGifts = (int)Math.ceil(getPastTimeAsClub() / 2678400.0);
        return totalGifts - this.hcGiftsClaimed;
    }

    public int getBuildersClubBonusFurni() {
        return this.buildersClubBonusFurni;
    }

    public void addBuildersClubBonusFurni(int amount) {
        if (amount <= 0) {
            return;
        }

        this.buildersClubBonusFurni += amount;
    }

    public THashMap<Achievement, Integer> getAchievementProgress() {
        return this.achievementProgress;
    }

    public THashMap<Achievement, Integer> getAchievementCache() {
        return this.achievementCache;
    }

    public void addPurchase(CatalogItem item) {
        if (!this.recentPurchases.containsKey(item.getId())) {
            this.recentPurchases.put(item.getId(), item);
        }
    }

    public THashMap<Integer, CatalogItem> getRecentPurchases() {
        return this.recentPurchases;
    }

    public void disposeRecentPurchases() {
        this.recentPurchases.clear();
    }

    public boolean addFavoriteRoom(int roomId) {
        if (this.favoriteRooms.contains(roomId))
            return false;

        if (Emulator.getConfig().getInt("hotel.rooms.max.favorite") <= this.favoriteRooms.size())
            return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_favorite_rooms (user_id, room_id) VALUES (?, ?)")) {
            statement.setInt(1, this.habboInfo.getId());
            statement.setInt(2, roomId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.favoriteRooms.add(roomId);
        return true;
    }

    public void removeFavoriteRoom(int roomId) {
        if (this.favoriteRooms.remove(roomId)) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM users_favorite_rooms WHERE user_id = ? AND room_id = ? LIMIT 1")) {
                statement.setInt(1, this.habboInfo.getId());
                statement.setInt(2, roomId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public boolean hasFavoriteRoom(int roomId) {
        return this.favoriteRooms.contains(roomId);
    }

    public boolean visitedRoom(int roomId) { return this.roomsVists.contains(roomId); }

    public void addVisitRoom(int roomId) { this.roomsVists.add(roomId); }

    public TIntArrayList getFavoriteRooms() {
        return this.favoriteRooms;
    }

    public boolean hasRecipe(int id) {
        return this.secretRecipes.contains(id);
    }

    public boolean addRecipe(int id) {
        if (this.secretRecipes.contains(id))
            return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_recipes (user_id, recipe) VALUES (?, ?)")) {
            statement.setInt(1, this.habboInfo.getId());
            statement.setInt(2, id);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.secretRecipes.add(id);
        return true;
    }

    public int talentTrackLevel(TalentTrackType type) {
        if (type == TalentTrackType.CITIZENSHIP)
            return this.citizenshipLevel;
        else if (type == TalentTrackType.HELPER)
            return this.helpersLevel;

        return -1;
    }

    public void setTalentLevel(TalentTrackType type, int level) {
        if (type == TalentTrackType.CITIZENSHIP)
            this.citizenshipLevel = level;
        else if (type == TalentTrackType.HELPER)
            this.helpersLevel = level;
    }

    public int getMuteEndTime() {
        return this.muteEndTime;
    }

    public int addMuteTime(int seconds) {
        if (this.remainingMuteTime() == 0) {
            this.muteEndTime = Emulator.getIntUnixTimestamp();
        }

        this.mutedBubbleTracker = true;
        this.muteEndTime += seconds;

        return this.remainingMuteTime();
    }

    public int remainingMuteTime() {
        return Math.max(0, this.muteEndTime - Emulator.getIntUnixTimestamp());
    }

    public boolean allowTalk() {
        return this.remainingMuteTime() == 0;
    }

    public void unMute() {
        this.muteEndTime = 0;
        this.mutedBubbleTracker = false;
    }

    public void addLtdLog(int catalogItemId, int timestamp) {
        if (!this.ltdPurchaseLog.containsKey(catalogItemId)) {
            this.ltdPurchaseLog.put(catalogItemId, new ArrayList<>(1));
        }

        this.ltdPurchaseLog.get(catalogItemId).add(timestamp);
    }

    public int totalLtds() {
        int total = 0;
        for (Map.Entry<Integer, List<Integer>> entry : this.ltdPurchaseLog.entrySet()) {
            total += entry.getValue().size();
        }

        return total;
    }

    public int totalLtds(int catalogItemId) {
        if (this.ltdPurchaseLog.containsKey(catalogItemId)) {
            return this.ltdPurchaseLog.get(catalogItemId).size();
        }

        return 0;
    }

    public boolean ignoreUser(GameClient gameClient, int userId) {
        final Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (!Emulator.getConfig().getBoolean("hotel.allow.ignore.staffs") && target.hasPermission(Permission.ACC_UNIGNORABLE)) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("generic.error.ignore_higher_rank"), RoomChatMessageBubbles.ALERT);
            return false;
        }

        if (!this.userIgnored(userId)) {
            this.ignoredUsers.add(userId);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO users_ignored (user_id, target_id) VALUES (?, ?)")) {
                statement.setInt(1, this.habboInfo.getId());
                statement.setInt(2, userId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        return true;
    }

    public void unignoreUser(int userId) {
        if (this.userIgnored(userId)) {
            this.ignoredUsers.remove(userId);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM users_ignored WHERE user_id = ? AND target_id = ?")) {
                statement.setInt(1, this.habboInfo.getId());
                statement.setInt(2, userId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public boolean userIgnored(int userId) {
        return this.ignoredUsers.contains(userId);
    }

    public boolean allowTrade() {
        if (AchievementManager.TALENTTRACK_ENABLED && RoomTrade.TRADING_REQUIRES_PERK)
            return this.perkTrade && this.allowTrade;
        else return this.allowTrade;
    }

    public boolean mentionsEnabled() {
        return this.mentionsEnabled;
    }

    public boolean massMentionsEnabled() {
        return this.massMentionsEnabled;
    }

    public void setMentionsEnabled(boolean enabled) {
        this.mentionsEnabled = enabled;
        persistFlag("mentions_enabled", enabled);
    }

    public void setMassMentionsEnabled(boolean enabled) {
        this.massMentionsEnabled = enabled;
        persistFlag("mass_mentions_enabled", enabled);
    }

    private void persistFlag(String column, boolean enabled) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET `" + column + "` = ? WHERE user_id = ? LIMIT 1")) {
            statement.setString(1, enabled ? "1" : "0");
            statement.setInt(2, this.habboInfo.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to persist users_settings.{} for user {}", column, this.habboInfo.getId(), e);
        }
    }

    private static String safeColumnString(ResultSet set, String column, String defaultValue) {
        try {
            String value = set.getString(column);
            return value == null ? defaultValue : value;
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    public void setAllowTrade(boolean allowTrade) {
        this.allowTrade = allowTrade;
    }

    public HabboOfferPurchase getHabboOfferPurchase(int offerId) {
        return this.offerCache.get(offerId);
    }

    public void addHabboOfferPurchase(HabboOfferPurchase offerPurchase) {
        this.offerCache.put(offerPurchase.getOfferId(), offerPurchase);
    }
}
