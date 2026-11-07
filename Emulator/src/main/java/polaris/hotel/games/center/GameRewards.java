package polaris.hotel.games.center;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.games.GameTexts;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;
import polaris.messages.outgoing.users.UserClothesComposer;

/**
 * Gives a game's reward to a player, online or not: a piece of furniture (in a gift), a set of clothes, a badge, or
 * credits / points. The reward is written to `gamecenter_reward_log`, and an online player gets a bubble.
 */
final class GameRewards {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameRewards.class);

    /** A reward of the configuration: its kind (play, win, week), its ranks for the week, what and how much. */
    record Reward(String game, String kind, int rankFrom, int rankTo, String type, String value, int amount) {}

    private GameRewards() {}

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }

    private static User online(int userId) {
        return environment().getUserManager().getUser(userId);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** Gives it; answers what was given, for the bubble, or null when nothing could be given. */
    static String give(int userId, Reward reward, String reason) {
        try {
            String given =
                    switch (reward.type()) {
                        case "currency" -> currency(userId, reward, reason);
                        case "badge" -> badge(userId, reward.value());
                        case "clothing" -> clothing(userId, reward.value());
                        case "furni" -> furni(userId, reward);
                        default -> null;
                    };

            if (given != null) log(userId, reward);

            return given;
        } catch (RuntimeException | SQLException e) {
            LOGGER.error("[gamecenter] could not give a reward", e);
            return null;
        }
    }

    private static String currency(int userId, Reward reward, String reason) throws SQLException {
        int amount = Math.max(0, Math.min(100_000, reward.amount()));
        int type = parse(reward.value(), -1);

        if (amount == 0) return null;

        User user = online(userId);
        String operation = EconomyOperationId.create("gamecenter:" + reward.game() + ":" + userId + ":" + type);

        if (user != null) {
            if (type < 0) user.giveCredits(amount, reason, operation, null);
            else user.givePoints(type, amount, reason, operation, null);
        } else {
            try {
                EconomyLedger.execute(new EconomyOperation(
                        operation,
                        userId,
                        null,
                        type < 0 ? "credit_grant" : "currency_grant",
                        reason,
                        type < 0 ? EconomyLedger.CREDITS : type,
                        amount,
                        null,
                        ""));
            } catch (Exception e) {
                throw new SQLException(e);
            }
        }

        return amount + " " + currencyName(type);
    }

    static String currencyName(int type) {
        return switch (type) {
            case -1 -> GameTexts.text("gamecenter.currency.credits", "credits");
            case 0 -> GameTexts.text("gamecenter.currency.duckets", "duckets");
            case 5 -> GameTexts.text("gamecenter.currency.diamonds", "diamonds");
            default ->
                GameTexts.text("gamecenter.currency.points", "points (%type%)").replace("%type%", String.valueOf(type));
        };
    }

    private static String badge(int userId, String code) throws SQLException {
        if (code == null || code.isBlank() || code.length() > 50) return null;

        User user = online(userId);

        if (user != null) {
            if (user.getInventory().getBadgesComponent().hasBadge(code)) return null;

            UserBadge badge = new UserBadge(0, code, 0, user);

            badge.run();
            user.getInventory().getBadgesComponent().addBadge(badge);
            user.getClient().sendResponse(new AddUserBadgeComposer(badge));

            return "le badge " + code;
        }

        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM users_badges WHERE user_id = ? AND badge_code = ? LIMIT 1")) {
                statement.setInt(1, userId);
                statement.setString(2, code);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return null;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users_badges (user_id, slot_id, badge_code) VALUES (?, 0, ?)")) {
                statement.setInt(1, userId);
                statement.setString(2, code);
                statement.executeUpdate();
            }
        }

        return "le badge " + code;
    }

    private static String clothing(int userId, String value) throws SQLException {
        int clothingId = parse(value, 0);

        if (clothingId <= 0) return null;

        String name = null;

        try (Connection connection = connection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT name FROM catalog_clothing WHERE id = ? LIMIT 1")) {
                statement.setInt(1, clothingId);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) name = result.getString(1);
                }
            }

            if (name == null) return null;

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO users_clothing (user_id, clothing_id) VALUES (?, ?)")) {
                statement.setInt(1, userId);
                statement.setInt(2, clothingId);
                statement.executeUpdate();
            }
        }

        User user = online(userId);

        if (user != null && user.getClient() != null) {
            user.getInventory().getWardrobeComponent().getClothing().add(clothingId);
            user.getClient().sendResponse(new UserClothesComposer(user));
        }

        return "une tenue";
    }

    /** The furniture comes in a gift, one a unit of the amount (5 at most). */
    private static String furni(int userId, Reward reward) {
        Item base = environment().getItemManager().getItem(parse(reward.value(), 0));

        if (base == null) return null;

        User user = online(userId);
        UserInfo info = user != null ? user.getUserInfo() : UserManager.getOfflineUserInfo(userId);
        Map<Integer, Integer> wrappings =
                environment().getCatalogManager().getGiftWrappingSnapshot().furniture();

        if (info == null || wrappings.isEmpty()) return null;

        int count = Math.max(1, Math.min(5, reward.amount()));
        Object[] wrappingIds = wrappings.values().toArray();

        for (int index = 0; index < count; index++) {
            UserItem content = environment().getItemManager().createGiftContent(userId, base, 0, 0, "");
            Item wrapping = environment().getItemManager().getItem((Integer)
                    wrappingIds[ThreadLocalRandom.current().nextInt(wrappingIds.length)]);

            if (content == null || wrapping == null) return null;

            String extra = "1\t" + content.getId() + "\t0\t0\t0\t"
                    + GameTexts.text("gamecenter.reward.gift", "Won at the Game Center!")
                            .replace("\t", " ")
                    + "\t0\t0";

            environment().getItemManager().createGift(info.getUsername(), wrapping, extra, 0, 0);
        }

        if (user != null && user.getClient() != null) user.getClient().sendResponse(new InventoryRefreshComposer());

        return (count > 1 ? count + " x " : "") + base.getFullName();
    }

    private static void log(int userId, Reward reward) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gamecenter_reward_log (user_id, game, kind, reward_type, reward_value, amount) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, userId);
            statement.setString(2, reward.game());
            statement.setString(3, reward.kind());
            statement.setString(4, reward.type());
            statement.setString(5, reward.value());
            statement.setInt(6, reward.amount());
            statement.executeUpdate();
        }
    }

    /** A bubble for an online player: what they won. */
    static void tell(int userId, String message) {
        User user = online(userId);

        if (user == null || user.getClient() == null) return;

        Map<String, String> keys = new HashMap<>();

        keys.put("display", "BUBBLE");
        keys.put("message", message);
        user.getClient().sendResponse(new BubbleAlertComposer("gamecenter.reward", keys));
    }

    static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
