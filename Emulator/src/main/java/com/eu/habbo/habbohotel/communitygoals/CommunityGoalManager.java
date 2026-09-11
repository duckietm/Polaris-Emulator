package com.eu.habbo.habbohotel.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
