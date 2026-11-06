package polaris.hotel.achievements;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import polaris.hotel.catalog.CatalogPurchaseMath;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.hotel.users.subscriptions.SubscriptionManager;

final class TalentTrackRewards {
    private TalentTrackRewards() {}

    static Result persist(
            Connection connection,
            User user,
            ItemManager itemManager,
            SubscriptionManager subscriptionManager,
            TalentTrackType type,
            int previousLevel,
            int nextLevel,
            List<TalentTrackLevel> levels,
            int timestamp)
            throws SQLException {
        int userId = user.getUserInfo().getId();
        String levelColumn =
                type == TalentTrackType.CITIZENSHIP ? "talent_track_citizenship_level" : "talent_track_helpers_level";
        List<UserItem> items = new ArrayList<>();
        List<UserBadge> badges = new ArrayList<>();
        boolean trade = levels.stream()
                .anyMatch(level -> level.perks != null
                        && java.util.Arrays.stream(level.perks).anyMatch("TRADE"::equalsIgnoreCase));
        int clubSeconds = CatalogPurchaseMath.checkedSubscriptionSeconds(
                levels.stream().mapToInt(level -> level.hcDays).reduce(0, Math::addExact));
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET " + levelColumn
                    + " = ?, perk_trade = IF(?, '1', perk_trade) WHERE user_id = ? AND " + levelColumn + " = ?")) {
                statement.setInt(1, nextLevel);
                statement.setBoolean(2, trade);
                statement.setInt(3, userId);
                statement.setInt(4, previousLevel);
                if (statement.executeUpdate() != 1) throw new SQLException("Talent milestone changed concurrently");
            }
            Subscription subscription =
                    clubSeconds > 0 ? grantClub(connection, subscriptionManager, userId, clubSeconds, timestamp) : null;
            for (TalentTrackLevel level : levels) {
                for (Item item : level.items) {
                    UserItem reward = itemManager.createItem(connection, userId, item, 0, 0, "");
                    if (reward == null) throw new SQLException("Unable to create talent reward " + item.getName());
                    items.add(reward);
                }
                if (level.badges == null) continue;
                for (String code : level.badges) {
                    if (code.isBlank()) continue;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id FROM users_badges WHERE user_id = ? AND badge_code = ?")) {
                        statement.setInt(1, userId);
                        statement.setString(2, code);
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) continue;
                        }
                    }
                    UserBadge badge = new UserBadge(0, code, 0, user);
                    badge.insert(connection);
                    badges.add(badge);
                }
            }
            connection.commit();
            return new Result(items, badges, trade, subscription, clubSeconds);
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    private static Subscription grantClub(
            Connection connection, SubscriptionManager manager, int userId, int seconds, int timestamp)
            throws SQLException {
        int id = 0;
        int start = timestamp;
        int duration = seconds;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, timestamp_start, duration FROM users_subscriptions WHERE user_id = ? AND subscription_type = ? AND active = 1 AND timestamp_start + duration > ? ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
            statement.setInt(1, userId);
            statement.setString(2, Subscription.CLUB);
            statement.setInt(3, timestamp);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    id = result.getInt("id");
                    start = result.getInt("timestamp_start");
                    duration = CatalogPurchaseMath.checkedAdd(result.getInt("duration"), seconds);
                }
            }
        }
        Math.addExact(start, duration);
        if (id > 0) {
            try (PreparedStatement statement =
                    connection.prepareStatement("UPDATE users_subscriptions SET duration = ? WHERE id = ?")) {
                statement.setInt(1, duration);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users_subscriptions (user_id,subscription_type,timestamp_start,duration,active) VALUES (?,?,?,?,1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, userId);
                statement.setString(2, Subscription.CLUB);
                statement.setInt(3, start);
                statement.setInt(4, duration);
                statement.executeUpdate();
                try (ResultSet result = statement.getGeneratedKeys()) {
                    if (!result.next()) throw new SQLException("Missing talent subscription ID");
                    id = result.getInt(1);
                }
            }
        }
        try {
            return manager.getSubscriptionClass(Subscription.CLUB)
                    .getConstructor(
                            Integer.class, Integer.class, String.class, Integer.class, Integer.class, Boolean.class)
                    .newInstance(id, userId, Subscription.CLUB, start, duration, true);
        } catch (ReflectiveOperationException exception) {
            throw new SQLException("Unable to construct talent subscription", exception);
        }
    }

    record Result(
            List<UserItem> items, List<UserBadge> badges, boolean trade, Subscription subscription, int clubSeconds) {}
}
