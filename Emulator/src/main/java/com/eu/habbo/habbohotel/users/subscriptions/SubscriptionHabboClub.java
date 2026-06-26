package com.eu.habbo.habbohotel.users.subscriptions;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.outgoing.catalog.ClubCenterDataComposer;
import com.eu.habbo.messages.outgoing.generic.PickMonthlyClubGiftNotificationComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Beny
 */
public class SubscriptionHabboClub extends Subscription {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionHabboClub.class);

    public static boolean HC_PAYDAY_ENABLED = false;
    public static int HC_PAYDAY_NEXT_DATE = Integer.MAX_VALUE; // yyyy-MM-dd HH:mm:ss
    public static String HC_PAYDAY_INTERVAL = "";
    public static String HC_PAYDAY_QUERY = "";
    public static TreeMap<Integer, Integer> HC_PAYDAY_STREAK = new TreeMap<>();
    public static String HC_PAYDAY_CURRENCY = "";
    public static Double HC_PAYDAY_KICKBACK_PERCENTAGE = 0.1;
    public static String ACHIEVEMENT_NAME = "";
    public static boolean DISCOUNT_ENABLED = false;
    public static int DISCOUNT_DAYS_BEFORE_END = 7;

    /**
     * When true "coins spent" will be calculated from the timestamp the user joins HC instead of from the last HC pay day execution timestamp
     */
    public static boolean HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE = false;

    /**
     * Boolean indicating if HC pay day currency executing. Prevents double execution
     */
    public static boolean isExecuting = false;

    public SubscriptionHabboClub(Integer id, Integer userId, String subscriptionType, Integer timestampStart, Integer duration, Boolean active) {
        super(id, userId, subscriptionType, timestampStart, duration, active);
    }

    /**
     * Called when the subscription is first created.
     * Actions:
     * - Set user's max_friends to MAXIMUM_FRIENDS_HC
     * - Set user's max_rooms to MAXIMUM_ROOMS_HC
     * - Reset the user's HC pay day timer (used in calculating the coins spent)
     * - Send associated HC packets to client
     */
    @Override
    public void onCreated() {
        super.onCreated();

        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(this.getUserId());
        HabboStats stats = habboInfo.getHabboStats();

        stats.maxFriends = Messenger.MAXIMUM_FRIENDS_HC;
        stats.maxRooms = RoomManager.MAXIMUM_ROOMS_HC;
        stats.lastHCPayday = HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE ? Emulator.getIntUnixTimestamp() : HC_PAYDAY_NEXT_DATE - Emulator.timeStringToSeconds(HC_PAYDAY_INTERVAL);
        Emulator.getThreading().run(stats);

        progressAchievement(habboInfo);

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getUserId());
        if (habbo != null && habbo.getClient() != null) {

            if (habbo.getHabboStats().getRemainingClubGifts() > 0) {
                habbo.getClient().sendResponse(new PickMonthlyClubGiftNotificationComposer(habbo.getHabboStats().getRemainingClubGifts()));
            }

            if ((Emulator.getIntUnixTimestamp() - habbo.getHabboStats().hcMessageLastModified) < 60) {
                Emulator.getThreading().run(() -> {
                    habbo.getClient().sendResponse(new UserClubComposer(habbo));
                    habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
                }, (Emulator.getIntUnixTimestamp() - habbo.getHabboStats().hcMessageLastModified));
            } else {
                habbo.getClient().sendResponse(new UserClubComposer(habbo, SubscriptionHabboClub.HABBO_CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
                habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            }
        }
    }

    /**
     * Called when the subscription is extended by manual action (by admin command or RCON)
     * Actions:
     * - Extend duration of the subscription
     * - Send associated HC packets to client
     */
    @Override
    public void addDuration(int amount) {
        super.addDuration(amount);

        if (amount < 0) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getUserId());
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UserClubComposer(habbo, SubscriptionHabboClub.HABBO_CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
                habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            }
        }
    }

    /**
     * Called when the subscription is extended or bought again when already exists
     * Actions:
     * - Extend duration of the subscription
     * - Send associated HC packets to client
     */
    @Override
    public void onExtended(int duration) {
        super.onExtended(duration);

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getUserId());

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new UserClubComposer(habbo, SubscriptionHabboClub.HABBO_CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
        }
    }

    /**
     * Called by SubscriptionScheduler when isActive() && getRemaining() < 0
     * Actions:
     * - Set user's max_friends to MAXIMUM_FRIENDS
     * - Set user's max_rooms to MAXIMUM_ROOMS
     * - Remove HC clothing
     * - Send associated HC packets to client
     */
    @Override
    public void onExpired() {
        super.onExpired();

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getUserId());
        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(this.getUserId());
        HabboStats stats = habboInfo.getHabboStats();

        stats.maxFriends = Messenger.MAXIMUM_FRIENDS;
        stats.maxRooms = RoomManager.MAXIMUM_ROOMS_USER;
        Emulator.getThreading().run(stats);

        if (habbo != null && ClothingValidationManager.VALIDATE_ON_HC_EXPIRE) {
            habboInfo.setLook(ClothingValidationManager.validateLook(habbo, habboInfo.getLook(), habboInfo.getGender().name()));
            Emulator.getThreading().run(habbo.getHabboInfo());

            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
            }

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
            }
        }

        if (habbo != null && habbo.getClient() != null) {
            habbo.getClient().sendResponse(new UserClubComposer(habbo, SubscriptionHabboClub.HABBO_CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
        }
    }

    /**
     * Calculate's a users upcoming HC Pay day rewards
     *
     * @param habbo User to calculate for
     * @return ClubCenterDataComposer
     */
    public static ClubCenterDataComposer calculatePayday(HabboInfo habbo) {
        Subscription activeSub = null;
        Subscription firstEverSub = null;
        int currentHcStreak = 0;
        int totalCreditsSpent = 0;
        int creditRewardForStreakBonus = 0;
        int creditRewardForMonthlySpent = 0;
        int timeUntilPayday = 0;

        for (Subscription sub : habbo.getHabboStats().subscriptions) {
            if (sub.getSubscriptionType().equalsIgnoreCase(Subscription.HABBO_CLUB)) {

                if (firstEverSub == null || sub.getTimestampStart() < firstEverSub.getTimestampStart()) {
                    firstEverSub = sub;
                }

                if (sub.isActive()) {
                    activeSub = sub;
                }
            }
        }

        if (HC_PAYDAY_ENABLED && activeSub != null) {
            currentHcStreak = (int) Math.floor((Emulator.getIntUnixTimestamp() - activeSub.getTimestampStart()) / (60 * 60 * 24.0));
            if (currentHcStreak < 1) {
                currentHcStreak = 0;
            }

            for (Map.Entry<Integer, Integer> set : HC_PAYDAY_STREAK.entrySet()) {
                if (currentHcStreak >= set.getKey() && set.getValue() > creditRewardForStreakBonus) {
                    creditRewardForStreakBonus = set.getValue();
                }
            }

            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("@user_id", habbo.getId());
            queryParams.put("@timestamp_start", habbo.getHabboStats().lastHCPayday);
            queryParams.put("@timestamp_end", HC_PAYDAY_NEXT_DATE);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = Database.preparedStatementWithParams(connection, HC_PAYDAY_QUERY, queryParams)) {

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        totalCreditsSpent = set.getInt("amount_spent");
                    }
                }

            } catch (SQLException e) {
                SubscriptionManager.LOGGER.error("Caught SQL exception", e);
            }

            creditRewardForMonthlySpent = (int) Math.floor(totalCreditsSpent * HC_PAYDAY_KICKBACK_PERCENTAGE);

            timeUntilPayday = (HC_PAYDAY_NEXT_DATE - Emulator.getIntUnixTimestamp()) / 60;
        }

        return new ClubCenterDataComposer(
                currentHcStreak,
                (firstEverSub != null ? new SimpleDateFormat("dd-MM-yyyy").format(new Date(firstEverSub.getTimestampStart() * 1000L)) : ""),
                HC_PAYDAY_KICKBACK_PERCENTAGE,
                0,
                0,
                totalCreditsSpent,
                creditRewardForStreakBonus,
                creditRewardForMonthlySpent,
                timeUntilPayday
        );
    }

    /**
     * Executes the HC Pay day, calculating reward for all active HABBO_CLUB subscribers and issuing rewards.
     */
    public static void executePayDay() {
        isExecuting = true;
        int timestampNow = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM `users_subscriptions` WHERE subscription_type = '" + Subscription.HABBO_CLUB + "' AND `active` = 1 AND `timestamp_start` < ? AND (`timestamp_start` + `duration`) > ? GROUP BY user_id")) {
            statement.setInt(1, timestampNow);
            statement.setInt(2, timestampNow);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        int userId = set.getInt("user_id");
                        HabboInfo habboInfo = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);
                        HabboStats stats = habboInfo.getHabboStats();
                        ClubCenterDataComposer calculated = calculatePayday(habboInfo);
                        int totalReward = (calculated.creditRewardForMonthlySpent + calculated.creditRewardForStreakBonus);
                        if (totalReward > 0) {
                            boolean claimed = claimPayDay(Emulator.getGameEnvironment().getHabboManager().getHabbo(userId), totalReward, HC_PAYDAY_CURRENCY);
                            HcPayDayLogEntry le = new HcPayDayLogEntry(timestampNow, userId, calculated.currentHcStreak, calculated.totalCreditsSpent, calculated.creditRewardForMonthlySpent, calculated.creditRewardForStreakBonus, totalReward, HC_PAYDAY_CURRENCY, claimed);
                            Emulator.getThreading().run(le);
                        }
                        stats.lastHCPayday = timestampNow;
                        Emulator.getThreading().run(stats);
                    } catch (Exception e) {
                        SubscriptionManager.LOGGER.error("Exception processing HC payday for user #{}", set.getInt("user_id"), e);
                    }
                }
            }

            var date = new java.util.Date(HC_PAYDAY_NEXT_DATE * 1000L);
            date = Emulator.modifyDate(date, HC_PAYDAY_INTERVAL);
            HC_PAYDAY_NEXT_DATE = (int) (date.getTime() / 1000L);

            try (PreparedStatement stm2 = connection.prepareStatement("UPDATE `emulator_settings` SET `value` = ? WHERE `key` = ?")) {
                var sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                stm2.setString(1, sdf.format(date));
                stm2.setString(2, "subscriptions.hc.payday.next_date");
                stm2.execute();
            }

            try (PreparedStatement stm2 = connection.prepareStatement("UPDATE users_settings SET last_hc_payday = ? WHERE user_id IN (SELECT user_id FROM `users_subscriptions` WHERE subscription_type = '" + Subscription.HABBO_CLUB + "' AND `active` = 1 AND `timestamp_start` < ? AND (`timestamp_start` + `duration`) > ? GROUP BY user_id)")) {
                stm2.setInt(1, timestampNow);
                stm2.setInt(2, timestampNow);
                stm2.setInt(3, timestampNow);
                stm2.execute();
            }

        } catch (SQLException e) {
            SubscriptionManager.LOGGER.error("Caught SQL exception", e);
        }
        isExecuting = false;
    }

    /**
     * Called when a user logs in. Checks for any unclaimed HC Pay day rewards and issues rewards.
     *
     * @param habbo User to process
     */
    public static void processUnclaimed(Habbo habbo) {

        progressAchievement(habbo.getHabboInfo());

        if (habbo.getHabboStats().getRemainingClubGifts() > 0) {
            habbo.getClient().sendResponse(new PickMonthlyClubGiftNotificationComposer(habbo.getHabboStats().getRemainingClubGifts()));
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM `logs_hc_payday` WHERE user_id = ? AND claimed = 0")) {
            statement.setInt(1, habbo.getHabboInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        int logId = set.getInt("id");
                        set.getInt("user_id"); // consumed but unused - user_id is in logs_hc_payday
                        int totalPayout = set.getInt("total_payout");
                        String currency = set.getString("currency");

                        if (claimPayDay(habbo, totalPayout, currency)) {
                            try (PreparedStatement stm2 = connection.prepareStatement("UPDATE logs_hc_payday SET claimed = 1 WHERE id = ?")) {
                                stm2.setInt(1, logId);
                                stm2.execute();
                            }
                        }
                    } catch (Exception e) {
                        SubscriptionManager.LOGGER.error("Exception processing HC payday for user #{}", set.getInt("user_id"), e);
                    }
                }
            }

        } catch (SQLException e) {
            SubscriptionManager.LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     *
     * Seperated these because Beny shouldn't have tied them to Payday.
     */
    public static void processClubBadge(Habbo habbo) {
        progressAchievement(habbo.getHabboInfo());
    }

    /**
     * Issues rewards to user.
     * @param habbo User to reward to
     * @param amount Amount of currency to reward
     * @param currency Currency string (Can be one of: credits, diamonds, duckets, pixels or a currency ID e.g. 5)
     * @return Boolean indicating success of the operation
     */
    public static boolean claimPayDay(Habbo habbo, int amount, String currency) {
        if(habbo == null)
            return false;

        int pointCurrency;
        switch(currency.toLowerCase()) {
            case "credits", "coins", "credit", "coin" -> habbo.getClient().getHabbo().giveCredits(amount);
            case "diamonds", "diamond" -> {
                pointCurrency = 5;
                habbo.getClient().getHabbo().givePoints(pointCurrency, amount);
            }
            case "duckets", "ducket", "pixels", "pixel" -> {
                pointCurrency = 0;
                habbo.getClient().getHabbo().givePoints(pointCurrency, amount);
            }
            default -> {
                pointCurrency = -1;
                try {
                    pointCurrency = Integer.parseInt(currency);
                }
                catch (NumberFormatException ex) {
                    LOGGER.error("Couldn't convert the type point currency {} on HC PayDay. The number must be a integer and positive.", pointCurrency);
                }

                if (pointCurrency >= 0) {
                    habbo.getClient().getHabbo().givePoints(pointCurrency, amount);
                }
            }
        }

        habbo.alert(Emulator.getTexts().getValue("subscriptions.hc.payday.message", "Woohoo HC Payday has arrived! You have received %amount% credits to your purse. Enjoy!").replace("%amount%", "" + amount));

        return true;
    }

    private static void progressAchievement(HabboInfo habboInfo) {
        HabboStats stats = habboInfo.getHabboStats();
        Achievement achievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement(ACHIEVEMENT_NAME);
        if(achievement != null) {
            int currentProgress = stats.getAchievementProgress(achievement);
            if(currentProgress == -1) {
                currentProgress = 0;
            }

            int progressToSet = (int)Math.ceil(stats.getPastTimeAsClub() / 2678400.0);
            int toIncrease = Math.max(progressToSet - currentProgress, 0);

            if(toIncrease > 0) {
                AchievementManager.progressAchievement(habboInfo.getId(), achievement, toIncrease);
            }
        }
    }

}
