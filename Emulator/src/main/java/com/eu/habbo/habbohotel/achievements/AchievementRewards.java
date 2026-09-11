package com.eu.habbo.habbohotel.achievements;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persists one achievement transition together with its badge, score and wallet rewards. */
final class AchievementRewards {
    private AchievementRewards() {}

    static Result persist(
            Connection connection,
            int userId,
            Achievement achievement,
            int previousProgress,
            int progress,
            List<AchievementLevel> earnedLevels,
            List<EconomyOperation> rewards,
            int queuedAmount,
            int currentScore)
            throws SQLException {
        connection.setAutoCommit(false);

        try {
            // Use the same lock order as the wallet ledger, including transitions without currency.
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                statement.setInt(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Unknown achievement user " + userId);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO users_achievements (user_id, achievement_name, progress) VALUES (?, ?, 0)")) {
                statement.setInt(1, userId);
                statement.setString(2, achievement.name);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users_achievements SET progress = ? WHERE user_id = ? AND achievement_name = ? AND progress = ?")) {
                statement.setInt(1, progress);
                statement.setInt(2, userId);
                statement.setString(3, achievement.name);
                statement.setInt(4, previousProgress);
                if (statement.executeUpdate() != 1) throw new SQLException("Achievement progress changed concurrently");
            }

            List<EconomyMutationResult> balances =
                    rewards.isEmpty() ? List.of() : EconomyLedger.applyBatch(connection, rewards);
            int score = 0;
            Badge badge = null;

            if (!earnedLevels.isEmpty()) {
                int points =
                        earnedLevels.stream().mapToInt(level -> level.points).reduce(0, Math::addExact);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_settings SET achievement_score = GREATEST(achievement_score, ?) + ? WHERE user_id = ?")) {
                    statement.setInt(1, currentScore);
                    statement.setInt(2, points);
                    statement.setInt(3, userId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Missing achievement user settings");
                }

                try (PreparedStatement statement =
                        connection.prepareStatement("SELECT achievement_score FROM users_settings WHERE user_id = ?")) {
                    statement.setInt(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        score = result.getInt(1);
                    }
                }

                badge = replaceBadge(connection, userId, achievement, earnedLevels.getLast().level);
            }

            if (queuedAmount > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_achievements_queue SET amount = GREATEST(0, amount - ?) WHERE user_id = ? AND achievement_id = ?")) {
                    statement.setInt(1, queuedAmount);
                    statement.setInt(2, userId);
                    statement.setInt(3, achievement.id);
                    statement.executeUpdate();
                }
            }

            connection.commit();
            return new Result(score, badge, balances);
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    private static Badge replaceBadge(Connection connection, int userId, Achievement achievement, int level)
            throws SQLException {
        int badgeId = 0;
        int slot = 0;
        List<Integer> obsoleteBadges = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, badge_code, slot_id FROM users_badges WHERE user_id = ? ORDER BY id FOR UPDATE")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!AchievementManager.isAchievementBadge(result.getString("badge_code"), achievement.name))
                        continue;
                    if (badgeId == 0) badgeId = result.getInt("id");
                    else obsoleteBadges.add(result.getInt("id"));
                    if (slot == 0) slot = result.getInt("slot_id");
                }
            }
        }

        String badgeCode = "ACH_" + achievement.name + level;
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM users_badges WHERE id = ? AND user_id = ?")) {
            statement.setInt(2, userId);
            for (int obsoleteBadge : obsoleteBadges) {
                statement.setInt(1, obsoleteBadge);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        if (badgeId > 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users_badges SET badge_code = ?, slot_id = ? WHERE id = ? AND user_id = ?")) {
                statement.setString(1, badgeCode);
                statement.setInt(2, slot);
                statement.setInt(3, badgeId);
                statement.setInt(4, userId);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users_badges (user_id, badge_code, slot_id) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, userId);
                statement.setString(2, badgeCode);
                statement.setInt(3, slot);
                statement.executeUpdate();
                try (ResultSet result = statement.getGeneratedKeys()) {
                    if (!result.next()) throw new SQLException("Missing achievement badge ID");
                    badgeId = result.getInt(1);
                }
            }
        }

        return new Badge(badgeId, badgeCode, slot);
    }

    record Badge(int id, String code, int slot) {}

    record Result(int score, Badge badge, List<EconomyMutationResult> balances) {}
}
