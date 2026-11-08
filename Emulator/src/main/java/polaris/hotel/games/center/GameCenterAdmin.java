package polaris.hotel.games.center;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.users.User;

/**
 * The Moderation's Games page (`acc_hk_games`): each game's free games a day, the free games a club member gets on
 * top, its packs of games (price and currency) and its rewards (at the end of a game, and for the week's
 * leaderboard). SnowStorm's settings stay where SnowStorm reads them: `gamecenter.games.free.daily` and `.hc`
 * (`emulator_settings`) and its three packs (`snowwar_token_offers`).
 */
public final class GameCenterAdmin {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameCenterAdmin.class);
    public static final String PERMISSION = "acc_hk_games";
    static final String SNOWWAR = "snowwar";
    private static final List<String> SNOWWAR_OFFERS =
            List.of("GET_SNOWWAR_TOKENS", "GET_SNOWWAR_TOKENS2", "GET_SNOWWAR_TOKENS3");
    private static final Set<String> KINDS = Set.of("play", "win", "week");
    private static final Set<String> TYPES = Set.of("furni", "clothing", "badge", "currency");
    private static final Pattern BADGE = Pattern.compile("[A-Za-z0-9_]{1,40}");
    private static final int MAX_OFFERS = 6;
    private static final int MAX_REWARDS = 60;

    private GameCenterAdmin() {}

    public static boolean allowed(int userId) {
        User user = GameCenter.onlineUser(userId);

        return user != null && user.hasPermission(PERMISSION);
    }

    private static List<String> games() {
        List<String> games = new ArrayList<>();

        games.add(SNOWWAR);
        games.addAll(GameCenter.GAMES);
        return games;
    }

    // ---- Reading

    public static JsonObject view() {
        JsonObject view = new JsonObject();
        JsonArray list = new JsonArray();

        for (String game : games()) list.add(game(game));

        JsonArray currencies = new JsonArray();

        for (int type : new int[] {-1, 0, 5}) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", type);
            entry.addProperty("name", GameRewards.currencyName(type));
            currencies.add(entry);
        }

        view.add("games", list);
        view.add("currencies", currencies);
        return view;
    }

    private static JsonObject game(String game) {
        JsonObject entry = new JsonObject();
        JsonArray offers = new JsonArray();

        entry.addProperty("id", game);

        if (game.equals(SNOWWAR)) {
            int free = GameCenter.setting("gamecenter.games.free.daily", 10);
            int club = GameCenter.setting("gamecenter.games.free.daily.hc", free);

            entry.addProperty("freeDaily", free);
            entry.addProperty("hcExtra", free < 0 || club < 0 ? -1 : Math.max(0, club - free));

            for (JsonObject offer : snowWarOffers()) offers.add(offer);
        } else {
            GameCenter.Settings settings = GameCenter.get().settings(game);

            entry.addProperty("freeDaily", settings.freeDaily());
            entry.addProperty("hcExtra", settings.hcExtra());

            for (GameCenter.Offer offer : GameCenter.get().offers(game)) {
                JsonObject item = new JsonObject();

                item.addProperty("games", offer.games());
                item.addProperty("price", offer.price());
                item.addProperty("currency", offer.currency());
                offers.add(item);
            }
        }

        JsonArray rewards = new JsonArray();

        for (GameRewards.Reward reward : GameCenter.get().rewards(game)) {
            JsonObject item = new JsonObject();

            item.addProperty("kind", reward.kind());
            item.addProperty("rankFrom", reward.rankFrom());
            item.addProperty("rankTo", reward.rankTo());
            item.addProperty("type", reward.type());
            item.addProperty("value", reward.value());
            item.addProperty("amount", reward.amount());
            item.addProperty("label", label(reward.type(), reward.value()));
            rewards.add(item);
        }

        entry.add("offers", offers);
        entry.add("rewards", rewards);
        return entry;
    }

    private static List<JsonObject> snowWarOffers() {
        List<JsonObject> offers = new ArrayList<>();
        String sql = "SELECT games, price_credits, price_points, points_type FROM snowwar_token_offers "
                + "WHERE enabled = '1' ORDER BY order_num, id";

        try (Connection connection = GameCenter.connection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                JsonObject offer = new JsonObject();
                boolean credits = result.getInt("price_points") <= 0;

                offer.addProperty("games", result.getInt("games"));
                offer.addProperty("price", credits ? result.getInt("price_credits") : result.getInt("price_points"));
                offer.addProperty("currency", credits ? -1 : result.getInt("points_type"));
                offers.add(offer);
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read SnowStorm's offers", e);
        }

        return offers;
    }

    /** A reward's name for the page: the furniture's or the clothes' name. */
    private static String label(String type, String value) {
        String sql =
                switch (type) {
                    case "furni" ->
                        "SELECT COALESCE(NULLIF(public_name, ''), item_name) FROM items_base WHERE id = ? LIMIT 1";
                    case "clothing" -> "SELECT name FROM catalog_clothing WHERE id = ? LIMIT 1";
                    default -> null;
                };

        if (sql == null)
            return type.equals("currency") ? GameRewards.currencyName(GameRewards.parse(value, -1)) : value;

        try (Connection connection = GameCenter.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, GameRewards.parse(value, 0));

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : "";
            }
        } catch (SQLException e) {
            return "";
        }
    }

    /** Furniture (items_base) or clothes (catalog_clothing) whose name or id matches. */
    public static JsonObject lookup(String type, String query) {
        JsonObject view = new JsonObject();
        JsonArray found = new JsonArray();
        String text = query == null ? "" : query.trim();
        String sql = type.equals("clothing")
                ? "SELECT id, name, CAST(setid AS CHAR) detail FROM catalog_clothing WHERE id = ? OR name LIKE ? "
                        + "ORDER BY id LIMIT 20"
                : "SELECT id, COALESCE(NULLIF(public_name, ''), item_name) name, item_name detail FROM items_base "
                        + "WHERE id = ? OR public_name LIKE ? OR item_name LIKE ? ORDER BY id LIMIT 20";

        if (text.length() >= 2) {
            try (Connection connection = GameCenter.connection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, GameRewards.parse(text, -1));
                statement.setString(2, "%" + text + "%");
                if (!type.equals("clothing")) statement.setString(3, "%" + text + "%");

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        JsonObject entry = new JsonObject();

                        entry.addProperty("id", result.getInt("id"));
                        entry.addProperty("name", result.getString("name"));
                        entry.addProperty("detail", result.getString("detail"));
                        found.add(entry);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("[gamecenter] lookup failed", e);
            }
        }

        view.add("found", found);
        return view;
    }

    // ---- Saving

    /** Saves a game's settings; answers the error code, or null. */
    public static String save(JsonObject body) {
        String game = string(body, "game");

        if (!games().contains(game)) return "no_game";

        int free = clamp(integer(body, "freeDaily", 10), -1, 1000);
        int hcExtra = clamp(integer(body, "hcExtra", 0), -1, 1000);
        List<int[]> offers = new ArrayList<>();
        List<GameRewards.Reward> rewards = new ArrayList<>();

        for (JsonElement element : array(body, "offers")) {
            JsonObject offer = element.getAsJsonObject();
            int games = integer(offer, "games", 0);
            int price = integer(offer, "price", -1);
            int currency = integer(offer, "currency", -1);

            if (games < 1 || games > 10_000 || price < 0 || price > 100_000 || currency < -1 || currency > 105)
                return "bad_offer";

            offers.add(new int[] {games, price, currency});
        }

        if (offers.size() > MAX_OFFERS) return "bad_offer";

        for (JsonElement element : array(body, "rewards")) {
            JsonObject reward = element.getAsJsonObject();
            String kind = string(reward, "kind");
            String type = string(reward, "type");
            String value = string(reward, "value").trim();
            int from = kind.equals("week") ? clamp(integer(reward, "rankFrom", 1), 1, 1000) : 1;
            int to = kind.equals("week") ? clamp(integer(reward, "rankTo", from), from, 1000) : 1;
            int amount = integer(reward, "amount", 1);

            if (!KINDS.contains(kind) || !TYPES.contains(type)) return "bad_reward";

            String problem =
                    switch (type) {
                        case "furni" -> label(type, value).isEmpty() || amount < 1 || amount > 5 ? "bad_furni" : null;
                        case "clothing" -> label(type, value).isEmpty() ? "bad_clothing" : null;
                        case "badge" -> BADGE.matcher(value).matches() ? null : "bad_badge";
                        default -> {
                            int currency = GameRewards.parse(value, -2);

                            yield currency < -1 || currency > 105 || amount < 1 || amount > 100_000
                                    ? "bad_currency"
                                    : null;
                        }
                    };

            if (problem != null) return problem;

            rewards.add(new GameRewards.Reward(
                    game, kind, from, to, type, value, type.equals("badge") || type.equals("clothing") ? 1 : amount));
        }

        if (rewards.size() > MAX_REWARDS) return "bad_reward";

        try (Connection connection = GameCenter.connection()) {
            connection.setAutoCommit(false);

            try {
                if (game.equals(SNOWWAR)) saveSnowWar(connection, free, hcExtra, offers);
                else saveGame(connection, game, free, hcExtra, offers);

                saveRewards(connection, game, rewards);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not save a game's settings", e);
            return "server_error";
        }

        GameCenter.get().forgetSettings();
        return null;
    }

    private static void saveGame(Connection connection, String game, int free, int hcExtra, List<int[]> offers)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gamecenter_settings (game, free_daily, hc_extra) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE free_daily = VALUES(free_daily), hc_extra = VALUES(hc_extra)")) {
            statement.setString(1, game);
            statement.setInt(2, free);
            statement.setInt(3, hcExtra);
            statement.executeUpdate();
        }

        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM gamecenter_offers WHERE game = ?")) {
            statement.setString(1, game);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gamecenter_offers (game, games, price, currency, order_num) VALUES (?, ?, ?, ?, ?)")) {
            for (int index = 0; index < offers.size(); index++) {
                statement.setString(1, game);
                statement.setInt(2, offers.get(index)[0]);
                statement.setInt(3, offers.get(index)[1]);
                statement.setInt(4, offers.get(index)[2]);
                statement.setInt(5, index + 1);
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    /** SnowStorm: its settings, and its three packs (the game's client knows them by their names). */
    private static void saveSnowWar(Connection connection, int free, int hcExtra, List<int[]> offers)
            throws SQLException {
        int club = free < 0 || hcExtra < 0 ? -1 : free + hcExtra;

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO emulator_settings (`key`, `value`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)")) {
            statement.setString(1, "gamecenter.games.free.daily");
            statement.setString(2, String.valueOf(free));
            statement.addBatch();
            statement.setString(1, "gamecenter.games.free.daily.hc");
            statement.setString(2, String.valueOf(club));
            statement.addBatch();
            statement.executeBatch();
        }

        GameCenter.updateSetting("gamecenter.games.free.daily", String.valueOf(free));
        GameCenter.updateSetting("gamecenter.games.free.daily.hc", String.valueOf(club));

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE snowwar_token_offers SET games = ?, price_credits = ?, price_points = ?, points_type = ?, "
                        + "enabled = ?, order_num = ? WHERE localization_id = ?")) {
            for (int index = 0; index < SNOWWAR_OFFERS.size(); index++) {
                int[] offer = index < offers.size() ? offers.get(index) : null;
                boolean credits = offer == null || offer[2] < 0;

                statement.setInt(1, offer == null ? 0 : offer[0]);
                statement.setInt(2, offer != null && credits ? offer[1] : 0);
                statement.setInt(3, offer != null && !credits ? offer[1] : 0);
                statement.setInt(4, offer != null && !credits ? offer[2] : 0);
                statement.setString(5, offer == null ? "0" : "1");
                statement.setInt(6, index + 1);
                statement.setString(7, SNOWWAR_OFFERS.get(index));
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private static void saveRewards(Connection connection, String game, List<GameRewards.Reward> rewards)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM gamecenter_rewards WHERE game = ?")) {
            statement.setString(1, game);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gamecenter_rewards (game, kind, rank_from, rank_to, reward_type, reward_value, amount) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (GameRewards.Reward reward : rewards) {
                statement.setString(1, game);
                statement.setString(2, reward.kind());
                statement.setInt(3, reward.rankFrom());
                statement.setInt(4, reward.rankTo());
                statement.setString(5, reward.type());
                statement.setString(6, reward.value());
                statement.setInt(7, reward.amount());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    // ---- JSON

    private static String string(JsonObject body, String key) {
        try {
            return body != null && body.has(key) && !body.get(key).isJsonNull()
                    ? body.get(key).getAsString()
                    : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static int integer(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonArray array(JsonObject body, String key) {
        return body != null && body.has(key) && body.get(key).isJsonArray()
                ? body.getAsJsonArray(key)
                : new JsonArray();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
