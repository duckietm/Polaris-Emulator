package com.eu.habbo.habbohotel.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrophy;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR 13 community goals: the tiered goal the hotel view shows
 * ({@code CommunityGoalProgress} 2525, {@code GetCommunityGoalProgress} 1145,
 * {@code CommunityGoalVote} 3536) and the concurrent-users goal
 * ({@code ConcurrentUsersGoalProgress} 2737, {@code GetConcurrentUsersGoalProgress}
 * 1343, {@code GetConcurrentUsersReward} 3872).
 *
 * <p>The tiered goal lives in {@code community_goals}; the concurrent-users goal
 * is derived from the number of players online against
 * {@code hotel.communitygoal.concurrentusers.goal}, which is what the official
 * server does too - it has no configuration of its own beyond the target.
 */
public class CommunityGoalManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityGoalManager.class);

    /** The goal code the concurrent-users reward is booked under. */
    public static final String CONCURRENT_USERS_CODE = "concurrentusers";

    /** `ConcurrentUsersInfoElementHandler` states of the official client. */
    public static final int CONCURRENT_USERS_DISABLED = 0;

    public static final int CONCURRENT_USERS_ACTIVE = 1;
    public static final int CONCURRENT_USERS_REDEEM = 2;
    public static final int CONCURRENT_USERS_REWARDED = 3;

    /** The AIR hall of fame shows ten avatars. */
    public static final int HALL_OF_FAME_MAX = 10;

    /** Upper bound of finished goals one earned prizes packet lists. */
    public static final int EARNED_PRIZES_MAX = 20;

    private final AtomicReference<CommunityGoal> activeGoal = new AtomicReference<>();

    public CommunityGoalManager() {
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info("Community Goal Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void reload() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, code, total_score, level_thresholds, reward_user_limits, vote_options, expires_at FROM community_goals WHERE enabled = 1 AND active = 1 ORDER BY id LIMIT 1");
                ResultSet set = statement.executeQuery()) {
            this.activeGoal.set(
                    set.next()
                            ? new CommunityGoal(
                                    set.getInt("id"),
                                    value(set.getString("code")),
                                    set.getInt("total_score"),
                                    numbers(set.getString("level_thresholds")),
                                    numbers(set.getString("reward_user_limits")),
                                    numbers(set.getString("vote_options")),
                                    set.getInt("expires_at"))
                            : null);
        } catch (SQLException e) {
            LOGGER.warn("Community goals could not be loaded; the hotel view falls back to an empty goal.", e);
            this.activeGoal.set(null);
        }
    }

    public CommunityGoal getActiveGoal() {
        return this.activeGoal.get();
    }

    /** The `CommunityGoalProgress` payload for one player. */
    public CommunityGoalProgress getProgress(int userId) {
        CommunityGoal goal = this.activeGoal.get();

        if (goal == null) {
            return new CommunityGoalProgress(true, 0, 0, 0, 0, 0, 0, "", 0, new int[0]);
        }

        int now = Emulator.getIntUnixTimestamp();
        int personalScore = 0;
        int personalRank = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT score FROM community_goal_contributions WHERE goal_id = ? AND user_id = ?")) {
                statement.setInt(1, goal.id());
                statement.setInt(2, userId);

                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) personalScore = set.getInt("score");
                }
            }

            if (personalScore > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) AS better FROM community_goal_contributions WHERE goal_id = ? AND score > ?")) {
                    statement.setInt(1, goal.id());
                    statement.setInt(2, personalScore);

                    try (ResultSet set = statement.executeQuery()) {
                        if (set.next()) personalRank = set.getInt("better") + 1;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read the community goal contribution of user {}", userId, e);
        }

        boolean expired = goal.expiresAt() > 0 && goal.expiresAt() <= now;

        return new CommunityGoalProgress(
                expired,
                personalScore,
                personalRank,
                goal.totalScore(),
                goal.highestAchievedLevel(),
                goal.scoreRemainingUntilNextLevel(),
                goal.percentCompletionTowardsNextLevel(),
                goal.code(),
                goal.expiresAt() > 0 ? Math.max(0, goal.expiresAt() - now) : 0,
                goal.rewardUserLimits());
    }

    /**
     * Adds to the active goal on behalf of a player: the community total and the
     * player's own contribution both move, so the meter and the rank stay in step.
     * This is the entry point other features (commands, plugins) use to feed a goal.
     */
    public boolean addContribution(int userId, int amount) {
        CommunityGoal goal = this.activeGoal.get();

        if (goal == null || amount <= 0 || userId <= 0) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO community_goal_contributions (goal_id, user_id, score, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE score = score + VALUES(score), updated_at = VALUES(updated_at)
                    """)) {
                statement.setInt(1, goal.id());
                statement.setInt(2, userId);
                statement.setInt(3, amount);
                statement.setInt(4, Emulator.getIntUnixTimestamp());
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE community_goals SET total_score = total_score + ? WHERE id = ?")) {
                statement.setInt(1, amount);
                statement.setInt(2, goal.id());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Could not add {} to community goal {}", amount, goal.code(), e);
            return false;
        }

        this.reload();
        return true;
    }

    /** `CommunityGoalVote` 3536: one vote per player, the first one counts. */
    public boolean vote(int userId, int optionId) {
        CommunityGoal goal = this.activeGoal.get();

        if (goal == null || userId <= 0 || !goal.hasVoteOption(optionId)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO community_goal_votes (goal_id, user_id, option_id, voted_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, goal.id());
            statement.setInt(2, userId);
            statement.setInt(3, optionId);
            statement.setInt(4, Emulator.getIntUnixTimestamp());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Could not save the community goal vote of user {}", userId, e);
            return false;
        }
    }

    public int getConcurrentUsersGoal() {
        return Math.max(0, Emulator.getConfig().getInt("hotel.communitygoal.concurrentusers.goal", 0));
    }

    public int getConcurrentUsersCount() {
        return Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().size();
    }

    /** The state the `element_concurrentusersinfo` element of the hotel view shows. */
    public int getConcurrentUsersState(int userId) {
        int goal = this.getConcurrentUsersGoal();

        if (goal <= 0) return CONCURRENT_USERS_DISABLED;
        if (this.getConcurrentUsersCount() < goal) return CONCURRENT_USERS_ACTIVE;

        return this.hasClaimed(CONCURRENT_USERS_CODE, userId) ? CONCURRENT_USERS_REWARDED : CONCURRENT_USERS_REDEEM;
    }

    /**
     * `GetConcurrentUsersReward` 3872: hands out the badge once, and only while
     * the hotel really is at or above the goal.
     */
    public boolean claimConcurrentUsersReward(Habbo habbo) {
        if (habbo == null) return false;

        int userId = habbo.getHabboInfo().getId();

        if (this.getConcurrentUsersState(userId) != CONCURRENT_USERS_REDEEM) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO community_goal_rewards_claimed (goal_code, user_id, claimed_at) VALUES (?, ?, ?)")) {
            statement.setString(1, CONCURRENT_USERS_CODE);
            statement.setInt(2, userId);
            statement.setInt(3, Emulator.getIntUnixTimestamp());

            if (statement.executeUpdate() != 1) return false;
        } catch (SQLException e) {
            LOGGER.error("Could not book the concurrent users reward of user {}", userId, e);
            return false;
        }

        String badge =
                Emulator.getConfig().getValue("hotel.communitygoal.concurrentusers.badge", "ConcurrentUsersReward");

        if (badge != null && !badge.isBlank()) habbo.addBadge(badge, "Community Goal");

        return true;
    }

    /** How many players the hall of fame lists; the AIR {@code UserListWidget} draws ten. */
    public int getHallOfFameSize() {
        return Math.max(
                1,
                Math.min(
                        HALL_OF_FAME_MAX,
                        Emulator.getConfig().getInt("hotel.communitygoal.halloffame.size", HALL_OF_FAME_MAX)));
    }

    /**
     * `GetCommunityGoalHallOfFame` 2167: the best contributors of the goal with
     * {@code goalCode}, or of the active goal when the code is empty. An unknown
     * code gives an empty list, which hides the widget.
     */
    public List<CommunityGoalHallOfFameEntry> getHallOfFame(String goalCode) {
        List<CommunityGoalHallOfFameEntry> entries = new ArrayList<>();
        int goalId = this.findGoalId(goalCode);

        if (goalId <= 0) return entries;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT c.user_id, c.score, u.username, u.look
                        FROM community_goal_contributions c
                        INNER JOIN users u ON u.id = c.user_id
                        WHERE c.goal_id = ? AND c.score > 0
                        ORDER BY c.score DESC, c.updated_at ASC, c.user_id ASC
                        LIMIT ?
                        """)) {
            statement.setInt(1, goalId);
            statement.setInt(2, this.getHallOfFameSize());

            try (ResultSet set = statement.executeQuery()) {
                List<Integer> scores = new ArrayList<>();
                List<Object[]> rows = new ArrayList<>();

                while (set.next()) {
                    scores.add(set.getInt("score"));
                    rows.add(new Object[] {
                        set.getInt("user_id"), value(set.getString("username")), value(set.getString("look"))
                    });
                }

                int[] ranks = competitionRanks(scores);

                for (int i = 0; i < rows.size(); i++) {
                    Object[] row = rows.get(i);
                    entries.add(new CommunityGoalHallOfFameEntry(
                            (Integer) row[0], (String) row[1], (String) row[2], ranks[i], scores.get(i)));
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read the hall of fame of community goal {}", goalCode, e);
        }

        return entries;
    }

    /** The code the hall of fame packet names: the asked one, or the active goal's. */
    public String resolveGoalCode(String goalCode) {
        if (goalCode != null && !goalCode.isBlank()) return goalCode;

        CommunityGoal goal = this.activeGoal.get();

        return goal == null ? "" : goal.code();
    }

    /**
     * `GetCommunityGoalEarnedPrizes` 2688: one prize per finished goal (expired)
     * in which the player's final rank falls into a rewarded bucket of
     * {@code reward_user_limits} that has a row in {@code community_goal_prizes},
     * and which the player did not redeem yet.
     */
    public List<CommunityGoalPrize> getEarnedPrizes(int userId) {
        List<CommunityGoalPrize> prizes = new ArrayList<>();

        if (userId <= 0) return prizes;

        int now = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            List<Object[]> finished = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT g.id, g.code, g.reward_user_limits, c.score
                    FROM community_goals g
                    INNER JOIN community_goal_contributions c ON c.goal_id = g.id AND c.user_id = ?
                    WHERE g.enabled = 1 AND g.expires_at > 0 AND g.expires_at <= ? AND c.score > 0
                    ORDER BY g.expires_at DESC
                    LIMIT ?
                    """)) {
                statement.setInt(1, userId);
                statement.setInt(2, now);
                statement.setInt(3, EARNED_PRIZES_MAX);

                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        finished.add(new Object[] {
                            set.getInt("id"),
                            value(set.getString("code")),
                            numbers(set.getString("reward_user_limits")),
                            set.getInt("score")
                        });
                    }
                }
            }

            for (Object[] goal : finished) {
                int goalId = (Integer) goal[0];
                String code = (String) goal[1];

                if (this.hasClaimed(code, userId)) continue;

                int rank = this.rankOf(connection, goalId, (Integer) goal[3]);
                int level = prizeLevelForRank(rank, (int[]) goal[2]);

                if (level <= 0) continue;

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT reward_code, is_badge, localized_name FROM community_goal_prizes WHERE goal_id = ? AND prize_level = ?")) {
                    statement.setInt(1, goalId);
                    statement.setInt(2, level);

                    try (ResultSet set = statement.executeQuery()) {
                        if (!set.next()) continue;

                        String rewardCode = value(set.getString("reward_code"));

                        if (rewardCode.isBlank()) continue;

                        prizes.add(new CommunityGoalPrize(
                                goalId,
                                code,
                                rank,
                                rewardCode,
                                set.getInt("is_badge") == 1,
                                value(set.getString("localized_name"))));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read the community goal prizes of user {}", userId, e);
        }

        return prizes;
    }

    /**
     * `RedeemCommunityGoalPrize` 90: hands out the prize of one finished goal once.
     * The claim row is booked first, so a double click cannot pay out twice.
     */
    public boolean redeemPrize(Habbo habbo, int communityGoalId) {
        if (habbo == null || communityGoalId <= 0) return false;

        int userId = habbo.getHabboInfo().getId();
        CommunityGoalPrize prize = null;

        for (CommunityGoalPrize candidate : this.getEarnedPrizes(userId)) {
            if (candidate.communityGoalId() == communityGoalId) {
                prize = candidate;
                break;
            }
        }

        if (prize == null) return false;

        Item baseItem = null;

        if (!prize.badge()) {
            baseItem = Emulator.getGameEnvironment().getItemManager().getItem(prize.rewardCode());

            if (baseItem == null) {
                LOGGER.warn(
                        "Community goal {} rewards the unknown item {}; nothing was handed out.",
                        prize.communityGoalCode(),
                        prize.rewardCode());
                return false;
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO community_goal_rewards_claimed (goal_code, user_id, claimed_at) VALUES (?, ?, ?)")) {
            statement.setString(1, prize.communityGoalCode());
            statement.setInt(2, userId);
            statement.setInt(3, Emulator.getIntUnixTimestamp());

            if (statement.executeUpdate() != 1) return false;
        } catch (SQLException e) {
            LOGGER.error("Could not book the community goal prize of user {}", userId, e);
            return false;
        }

        if (prize.badge()) {
            habbo.addBadge(prize.rewardCode(), "Community Goal");
            return true;
        }

        String extraData = "";

        if (baseItem.getInteractionType().getType() == InteractionTrophy.class) {
            extraData = trophyExtraData(
                    habbo.getHabboInfo().getUsername(),
                    LocalDate.now(),
                    this.trophyInscription(prize.communityGoalId(), prize.communityGoalCode(), prize.userRank()));
        }

        HabboItem reward = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, 0, 0, extraData);

        if (reward == null) return false;

        habbo.getInventory().getItemsComponent().addItem(reward);

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new AddHabboItemComposer(reward));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }

        return true;
    }

    /**
     * The prize bucket of a rank: bucket 1 covers ranks 1..limits[0], bucket 2 the
     * next limits[1] ranks, and so on - the same split the AIR
     * {@code CommunityGoalPrizesWidget.setPrizeRankLimits} prints. 0 = no prize.
     */
    public static int prizeLevelForRank(int rank, int[] rewardUserLimits) {
        if (rank <= 0 || rewardUserLimits == null) return 0;

        int end = 0;

        for (int level = 0; level < rewardUserLimits.length; level++) {
            end += Math.max(0, rewardUserLimits[level]);

            if (rank <= end) return level + 1;
        }

        return 0;
    }

    /** Ranks of a score list sorted high to low; equal scores share a rank (1, 2, 2, 4). */
    public static int[] competitionRanks(List<Integer> scoresDescending) {
        int[] ranks = new int[scoresDescending.size()];

        for (int i = 0; i < ranks.length; i++) {
            ranks[i] = i > 0 && scoresDescending.get(i).equals(scoresDescending.get(i - 1)) ? ranks[i - 1] : i + 1;
        }

        return ranks;
    }

    /** The extradata of a trophy: name, date and inscription separated by tabs. */
    public static String trophyExtraData(String username, LocalDate date, String inscription) {
        return username
                + (char) 9
                + date.getDayOfMonth()
                + "-"
                + date.getMonthValue()
                + "-"
                + date.getYear()
                + (char) 9
                + inscription;
    }

    private String trophyInscription(int goalId, String goalCode, int rank) {
        String inscription = "";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT trophy_inscription FROM community_goal_prizes WHERE goal_id = ? AND prize_level = ?")) {
            statement.setInt(1, goalId);
            statement.setInt(2, prizeLevelForRank(rank, this.limitsOf(connection, goalId)));

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) inscription = value(set.getString("trophy_inscription"));
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read the trophy inscription of community goal {}", goalCode, e);
        }

        if (inscription.isBlank()) inscription = goalCode + " #%rank%";

        return inscription.replace("%rank%", String.valueOf(rank)).replace("{0}", String.valueOf(rank));
    }

    private int[] limitsOf(Connection connection, int goalId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT reward_user_limits FROM community_goals WHERE id = ?")) {
            statement.setInt(1, goalId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? numbers(set.getString("reward_user_limits")) : new int[0];
            }
        }
    }

    private int rankOf(Connection connection, int goalId, int score) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) AS better FROM community_goal_contributions WHERE goal_id = ? AND score > ?")) {
            statement.setInt(1, goalId);
            statement.setInt(2, score);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("better") + 1 : 0;
            }
        }
    }

    private int findGoalId(String goalCode) {
        if (goalCode == null || goalCode.isBlank()) {
            CommunityGoal goal = this.activeGoal.get();

            return goal == null ? 0 : goal.id();
        }

        if (goalCode.length() > 64) return 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM community_goals WHERE code = ? AND enabled = 1")) {
            statement.setString(1, goalCode);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("id") : 0;
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not look up community goal {}", goalCode, e);
            return 0;
        }
    }

    private boolean hasClaimed(String goalCode, int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM community_goal_rewards_claimed WHERE goal_code = ? AND user_id = ?")) {
            statement.setString(1, goalCode);
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not read the community goal reward of user {}", userId, e);
            return false;
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static int[] numbers(String value) {
        if (value == null || value.isBlank()) return new int[0];

        List<Integer> parsed = new ArrayList<>();

        for (String part : value.split(",")) {
            try {
                parsed.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed entry is skipped; the rest of the row still works.
            }
        }

        int[] result = new int[parsed.size()];
        for (int i = 0; i < result.length; i++) result[i] = parsed.get(i);
        return result;
    }

    public void dispose() {
        this.activeGoal.set(null);
        LOGGER.info("Community Goal Manager -> Disposed!");
    }
}
