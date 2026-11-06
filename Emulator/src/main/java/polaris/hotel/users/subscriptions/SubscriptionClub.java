package polaris.hotel.users.subscriptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.earnings.EarningsCategory;
import polaris.hotel.earnings.EarningsNotifications;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.messages.outgoing.catalog.ClubCenterDataComposer;
import polaris.messages.outgoing.generic.PickMonthlyClubGiftNotificationComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;
import polaris.messages.outgoing.users.UserClubComposer;
import polaris.messages.outgoing.users.UserPermissionsComposer;

/**
 * @author Beny
 */
public class SubscriptionClub extends Subscription {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionClub.class);

    public static volatile boolean HC_PAYDAY_ENABLED = false;
    public static volatile int HC_PAYDAY_NEXT_DATE = Integer.MAX_VALUE; // yyyy-MM-dd HH:mm:ss
    public static volatile String HC_PAYDAY_INTERVAL = "";
    public static volatile String HC_PAYDAY_QUERY = "";
    public static volatile TreeMap<Integer, Integer> HC_PAYDAY_STREAK = new TreeMap<>();
    public static volatile String HC_PAYDAY_CURRENCY = "";
    public static volatile Double HC_PAYDAY_KICKBACK_PERCENTAGE = 0.1;
    public static volatile String ACHIEVEMENT_NAME = "";
    public static volatile boolean DISCOUNT_ENABLED = false;
    public static volatile int DISCOUNT_DAYS_BEFORE_END = 7;

    /**
     * When true "coins spent" will be calculated from the timestamp the user joins HC instead of from the last HC pay day execution timestamp
     */
    public static volatile boolean HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE = false;

    /**
     * Boolean indicating if HC pay day currency executing. Prevents double execution
     */
    public static boolean isExecuting = false;

    public SubscriptionClub(
            Integer id,
            Integer userId,
            String subscriptionType,
            Integer timestampStart,
            Integer duration,
            Boolean active) {
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

        UserInfo userInfo = Emulator.getGameEnvironment().getUserManager().getUserInfo(this.getUserId());
        UserStats stats = userInfo.getUserStats();

        stats.maxFriends = Messenger.MAXIMUM_FRIENDS_HC;
        stats.maxRooms = RoomManager.MAXIMUM_ROOMS_HC;
        stats.lastHCPayday = HC_PAYDAY_COINSSPENT_RESET_ON_EXPIRE
                ? Emulator.getIntUnixTimestamp()
                : HC_PAYDAY_NEXT_DATE - Emulator.timeStringToSeconds(HC_PAYDAY_INTERVAL);
        Emulator.getThreading().run(stats);

        progressAchievement(userInfo);

        User user = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());
        if (user != null && user.getClient() != null) {

            if (user.getUserStats().getRemainingClubGifts() > 0) {
                user.getClient()
                        .sendResponse(new PickMonthlyClubGiftNotificationComposer(
                                user.getUserStats().getRemainingClubGifts()));
            }

            if ((Emulator.getIntUnixTimestamp() - user.getUserStats().hcMessageLastModified) < 60) {
                Emulator.getThreading()
                        .run(
                                () -> {
                                    user.getClient().sendResponse(new UserClubComposer(user));
                                    user.getClient().sendResponse(new UserPermissionsComposer(user));
                                },
                                (Emulator.getIntUnixTimestamp() - user.getUserStats().hcMessageLastModified));
            } else {
                user.getClient()
                        .sendResponse(new UserClubComposer(
                                user, SubscriptionClub.CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
                user.getClient().sendResponse(new UserPermissionsComposer(user));
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
            User user = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());
            if (user != null && user.getClient() != null) {
                user.getClient()
                        .sendResponse(new UserClubComposer(
                                user, SubscriptionClub.CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
                user.getClient().sendResponse(new UserPermissionsComposer(user));
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

        User user = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());

        if (user != null && user.getClient() != null) {
            user.getClient()
                    .sendResponse(
                            new UserClubComposer(user, SubscriptionClub.CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
            user.getClient().sendResponse(new UserPermissionsComposer(user));
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

        User user = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());
        UserInfo userInfo = Emulator.getGameEnvironment().getUserManager().getUserInfo(this.getUserId());
        UserStats stats = userInfo.getUserStats();

        stats.maxFriends = Messenger.MAXIMUM_FRIENDS;
        stats.maxRooms = RoomManager.MAXIMUM_ROOMS_USER;
        Emulator.getThreading().run(stats);

        if (user != null && ClothingValidationManager.VALIDATE_ON_HC_EXPIRE) {
            userInfo.setLook(ClothingValidationManager.validateLook(
                    user, userInfo.getLook(), userInfo.getGender().name()));
            Emulator.getThreading().run(user.getUserInfo());

            if (user.getClient() != null) {
                user.getClient().sendResponse(new UpdateUserLookComposer(user));
            }

            if (user.getUserInfo().getCurrentRoom() != null) {
                user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
            }
        }

        if (user != null && user.getClient() != null) {
            user.getClient()
                    .sendResponse(
                            new UserClubComposer(user, SubscriptionClub.CLUB, UserClubComposer.RESPONSE_TYPE_NORMAL));
            user.getClient().sendResponse(new UserPermissionsComposer(user));
        }
    }

    /**
     * Calculate's a users upcoming HC Pay day rewards
     *
     * @param user User to calculate for
     * @return ClubCenterDataComposer
     */
    public static ClubCenterDataComposer calculatePayday(UserInfo user) {
        Subscription activeSub = null;
        Subscription firstEverSub = null;
        int currentHcStreak = 0;
        int totalCreditsSpent = 0;
        int creditRewardForStreakBonus = 0;
        int creditRewardForMonthlySpent = 0;
        int timeUntilPayday = 0;

        for (Subscription sub : user.getUserStats().subscriptions) {
            if (sub.getSubscriptionType().equalsIgnoreCase(Subscription.CLUB)) {

                if (firstEverSub == null || sub.getTimestampStart() < firstEverSub.getTimestampStart()) {
                    firstEverSub = sub;
                }

                if (sub.isActive()) {
                    activeSub = sub;
                }
            }
        }

        if (HC_PAYDAY_ENABLED && activeSub != null) {
            currentHcStreak = (int)
                    Math.floor((Emulator.getIntUnixTimestamp() - activeSub.getTimestampStart()) / (60 * 60 * 24.0));
            if (currentHcStreak < 1) {
                currentHcStreak = 0;
            }

            for (Map.Entry<Integer, Integer> set : HC_PAYDAY_STREAK.entrySet()) {
                if (currentHcStreak >= set.getKey() && set.getValue() > creditRewardForStreakBonus) {
                    creditRewardForStreakBonus = set.getValue();
                }
            }

            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("@user_id", user.getId());
            queryParams.put("@timestamp_start", user.getUserStats().lastHCPayday);
            queryParams.put("@timestamp_end", HC_PAYDAY_NEXT_DATE);

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            Database.preparedStatementWithParams(connection, HC_PAYDAY_QUERY, queryParams)) {

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
                (firstEverSub != null
                        ? new SimpleDateFormat("dd-MM-yyyy").format(new Date(firstEverSub.getTimestampStart() * 1000L))
                        : ""),
                HC_PAYDAY_KICKBACK_PERCENTAGE,
                0,
                0,
                totalCreditsSpent,
                creditRewardForStreakBonus,
                creditRewardForMonthlySpent,
                timeUntilPayday);
    }

    /**
     * Executes the HC Pay day, calculating reward for all active CLUB subscribers and issuing rewards.
     */
    public static void executePayDay() {
        isExecuting = true;
        int timestampNow = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT user_id FROM `users_subscriptions` WHERE subscription_type = '"
                                + Subscription.CLUB
                                + "' AND `active` = 1 AND `timestamp_start` < ? AND (`timestamp_start` + `duration`) > ? GROUP BY user_id")) {
            statement.setInt(1, timestampNow);
            statement.setInt(2, timestampNow);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        int userId = set.getInt("user_id");
                        UserInfo userInfo =
                                Emulator.getGameEnvironment().getUserManager().getUserInfo(userId);
                        UserStats stats = userInfo.getUserStats();
                        ClubCenterDataComposer calculated = calculatePayday(userInfo);
                        int totalReward =
                                (calculated.creditRewardForMonthlySpent + calculated.creditRewardForStreakBonus);
                        if (totalReward > 0) {
                            User online = Emulator.getGameEnvironment()
                                    .getUserManager()
                                    .getUser(userId);
                            // With the income window on, the payday waits there (as in AIR) instead of going
                            // straight into the purse.
                            boolean waits = paydayWaitsInIncomeWindow();
                            boolean claimed = !waits && claimPayDay(online, totalReward, HC_PAYDAY_CURRENCY);
                            HcPayDayLogEntry le = new HcPayDayLogEntry(
                                    timestampNow,
                                    userId,
                                    calculated.currentHcStreak,
                                    calculated.totalCreditsSpent,
                                    calculated.creditRewardForMonthlySpent,
                                    calculated.creditRewardForStreakBonus,
                                    totalReward,
                                    HC_PAYDAY_CURRENCY,
                                    claimed);
                            if (waits) {
                                // Written now, so the row is claimable when the player is told about it.
                                le.run();
                                EarningsNotifications.newReward(online, EarningsCategory.HC_PAYDAY);
                            } else {
                                Emulator.getThreading().run(le);
                            }
                        }
                        stats.lastHCPayday = timestampNow;
                        Emulator.getThreading().run(stats);
                    } catch (Exception e) {
                        SubscriptionManager.LOGGER.error(
                                "Exception processing HC payday for user #{}", set.getInt("user_id"), e);
                    }
                }
            }

            Date date = new java.util.Date(HC_PAYDAY_NEXT_DATE * 1000L);
            date = Emulator.modifyDate(date, HC_PAYDAY_INTERVAL);
            HC_PAYDAY_NEXT_DATE = (int) (date.getTime() / 1000L);

            try (PreparedStatement stm2 =
                    connection.prepareStatement("UPDATE `emulator_settings` SET `value` = ? WHERE `key` = ?")) {
                SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                stm2.setString(1, sdf.format(date));
                stm2.setString(2, "subscriptions.hc.payday.next_date");
                stm2.execute();
            }

            try (PreparedStatement stm2 = connection.prepareStatement(
                    "UPDATE users_settings SET last_hc_payday = ? WHERE user_id IN (SELECT user_id FROM `users_subscriptions` WHERE subscription_type = '"
                            + Subscription.CLUB
                            + "' AND `active` = 1 AND `timestamp_start` < ? AND (`timestamp_start` + `duration`) > ? GROUP BY user_id)")) {
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
     * @param user User to process
     */
    public static void processUnclaimed(User user) {

        progressAchievement(user.getUserInfo());

        if (user.getUserStats().getRemainingClubGifts() > 0) {
            user.getClient()
                    .sendResponse(new PickMonthlyClubGiftNotificationComposer(
                            user.getUserStats().getRemainingClubGifts()));
        }

        // The income window collects the payday rows; the client lights the purse indicator from its first status.
        if (paydayWaitsInIncomeWindow()) return;

        claimUnclaimedPayDays(user);
    }

    /** Whether unpaid HC paydays wait in the income window (its HC payday row) instead of paying at once. */
    public static boolean paydayWaitsInIncomeWindow() {
        return EarningsNotifications.collectsInWindow(EarningsCategory.HC_PAYDAY);
    }

    /**
     * Pays out the payday rows the user has not collected yet.
     *
     * @return how many rows were paid
     */
    public static int claimUnclaimedPayDays(User user) {
        int paid = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM `logs_hc_payday` WHERE user_id = ? AND claimed = 0")) {
            statement.setInt(1, user.getUserInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        int logId = set.getInt("id");
                        set.getInt("user_id"); // consumed but unused - user_id is in logs_hc_payday
                        int totalPayout = set.getInt("total_payout");
                        String currency = set.getString("currency");

                        // Claim the row before paying anything. The payout used to come
                        // first and the row was marked afterwards with no condition, so
                        // two sessions of the same account reading the same unclaimed
                        // row each paid it out.
                        if (!claimPayDayRow(connection, logId)) continue;

                        if (claimPayDay(user, totalPayout, currency)) {
                            paid++;
                        } else {
                            releasePayDayRow(connection, logId);
                        }
                    } catch (Exception e) {
                        SubscriptionManager.LOGGER.error(
                                "Exception processing HC payday for user #{}", set.getInt("user_id"), e);
                    }
                }
            }

        } catch (SQLException e) {
            SubscriptionManager.LOGGER.error("Caught SQL exception", e);
        }

        return paid;
    }

    /**
     * Takes one payday row, if it is still unclaimed.
     *
     * @return whether this caller is the one that claimed it
     */
    private static boolean claimPayDayRow(Connection connection, int logId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("UPDATE logs_hc_payday SET claimed = 1 WHERE id = ? AND claimed = 0")) {
            statement.setInt(1, logId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Puts a claimed row back when the payout itself did not happen. */
    private static void releasePayDayRow(Connection connection, int logId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("UPDATE logs_hc_payday SET claimed = 0 WHERE id = ?")) {
            statement.setInt(1, logId);
            statement.executeUpdate();
        }
    }

    /**
     *
     * Seperated these because Beny shouldn't have tied them to Payday.
     */
    public static void processClubBadge(User user) {
        progressAchievement(user.getUserInfo());
    }

    /**
     * Issues rewards to user.
     * @param user User to reward to
     * @param amount Amount of currency to reward
     * @param currency Currency string (Can be one of: credits, diamonds, duckets, pixels or a currency ID e.g. 5)
     * @return Boolean indicating success of the operation
     */
    public static boolean claimPayDay(User user, int amount, String currency) {
        if (user == null) return false;

        int pointCurrency;
        switch (currency.toLowerCase()) {
            case "credits":
            case "coins":
            case "credit":
            case "coin":
                user.getClient().getUser().giveCredits(amount);
                break;

            case "diamonds":
            case "diamond":
                pointCurrency = 5;
                user.getClient().getUser().givePoints(pointCurrency, amount);
                break;

            case "duckets":
            case "ducket":
            case "pixels":
            case "pixel":
                pointCurrency = 0;
                user.getClient().getUser().givePoints(pointCurrency, amount);
                break;

            default:
                pointCurrency = -1;
                try {
                    pointCurrency = Integer.parseInt(currency);
                } catch (NumberFormatException ex) {
                    LOGGER.error(
                            "Couldn't convert the type point currency {} on HC PayDay. The number must be a integer and positive.",
                            pointCurrency);
                }

                if (pointCurrency >= 0) {
                    user.getClient().getUser().givePoints(pointCurrency, amount);
                }
                break;
        }

        user.alert(Emulator.getTexts()
                .getValue(
                        "subscriptions.hc.payday.message",
                        "Woohoo HC Payday has arrived! You have received %amount% credits to your purse. Enjoy!")
                .replace("%amount%", "" + amount));

        return true;
    }

    private static void progressAchievement(UserInfo userInfo) {
        UserStats stats = userInfo.getUserStats();
        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(ACHIEVEMENT_NAME);
        if (achievement != null) {
            int currentProgress = stats.getAchievementProgress(achievement);
            if (currentProgress == -1) {
                currentProgress = 0;
            }

            int progressToSet = (int) Math.ceil(stats.getPastTimeAsClub() / 2678400.0);
            int toIncrease = Math.max(progressToSet - currentProgress, 0);

            if (toIncrease > 0) {
                AchievementManager.progressAchievement(userInfo.getId(), achievement, toIncrease);
            }
        }
    }
}
