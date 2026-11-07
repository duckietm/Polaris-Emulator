package polaris.hotel.games.center;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogPaymentService;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Habbi's Game Center for the games other than SnowStorm (which keeps its own): the free games of the day (fewer
 * without the Habbo Club), the games bought on top, game by game, and the scores of the week and of all time.
 *
 * <p>A game is charged to each player when it starts (robots aside); a player with no game left cannot start or join
 * one. Free games a day: {@code gamecenter.<game>.free.daily} (.hc for a club member), or by default the same as
 * SnowStorm, {@code gamecenter.games.free.daily} and {@code gamecenter.games.free.daily.hc}; -1 is unlimited. The packs
 * of games are SnowStorm's ({@code snowwar_token_offers}), bought for one game at a time.
 *
 * <p>Scores: a win is worth {@link #WIN_POINTS}, a game played {@link #PLAY_POINTS}; added up by ISO week in
 * {@code game_scores}.
 */
public final class GameCenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameCenter.class);
    private static final GameCenter INSTANCE = new GameCenter();

    /** The games counted here, in the Game Center's order. */
    public static final List<String> GAMES = List.of(
            "uno",
            "werewolf",
            "wobble",
            "wobblenext",
            "battleball",
            "fastfood",
            "hall",
            "snails",
            "quiz",
            "tetris",
            "diving",
            "kart",
            "copywriter");

    public static final int WIN_POINTS = 10;
    public static final int PLAY_POINTS = 2;

    /** A pack of games, as sold for SnowStorm. */
    public record Offer(int id, int games, int credits, int points, int pointsType) {}

    /** A line of a leaderboard: a player, or a group (gender "g", figure = its badge). */
    public record Entry(int id, int rank, String name, String figure, String gender, int score) {}

    private GameCenter() {}

    public static GameCenter get() {
        return INSTANCE;
    }

    /** The game hall's own games count as one game, "hall"; the games with their own world as themselves. */
    public static String keyOfHall(String kind) {
        return switch (kind) {
            case "tictactoe", "connect4", "battleships", "chess", "poker" -> "hall";
            default -> kind;
        };
    }

    public static boolean known(String game) {
        return GAMES.contains(game);
    }

    // ---- The emulator's services, each reached in one place

    private static int setting(String key, int fallback) {
        return Emulator.getConfig().getInt(key, fallback);
    }

    private static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static void later(Runnable task) {
        Emulator.getThreading().run(task);
    }

    // ---- Games left

    /** Free games a day for this player in this game; -1 unlimited. */
    public int freePerDay(int userId, String game) {
        int everyone = setting("gamecenter." + game + ".free.daily", setting("gamecenter.games.free.daily", 10));
        int club =
                setting("gamecenter." + game + ".free.daily.hc", setting("gamecenter.games.free.daily.hc", everyone));
        User user = onlineUser(userId);
        boolean member = user != null && user.getUserStats().hasActiveClub();

        if (everyone < 0) return -1;
        if (!member) return everyone;

        return club < 0 ? -1 : Math.max(everyone, club);
    }

    /** [free used today, extra bought] of a player in a game. */
    private int[] usage(Connection connection, int userId, String game) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT free_day, free_used, extra FROM game_plays WHERE user_id = ? AND game = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, game);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new int[] {0, 0};

                Date day = result.getDate("free_day");
                boolean today = day != null && day.toLocalDate().equals(LocalDate.now());

                return new int[] {today ? result.getInt("free_used") : 0, Math.max(0, result.getInt("extra"))};
            }
        }
    }

    /** The games this player has left today in this game: free ones, then bought ones; -1 unlimited. */
    public int gamesLeft(int userId, String game) {
        int free = this.freePerDay(userId, game);

        if (free < 0) return -1;

        try (Connection connection = connection()) {
            int[] usage = this.usage(connection, userId, game);

            return Math.max(0, free - usage[0]) + usage[1];
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read the games left", e);
            return 1;
        }
    }

    /** The first of these players (robots aside) who has no game left, or 0. */
    public int firstWithoutGames(Collection<Integer> userIds, String game) {
        for (int userId : userIds) {
            if (userId > 0 && this.gamesLeft(userId, game) == 0) return userId;
        }

        return 0;
    }

    /** A game starts: one game is used by each of these players, a free one first. */
    public void charge(Collection<Integer> userIds, String game) {
        List<Integer> players = userIds.stream().filter(id -> id > 0).toList();

        if (players.isEmpty()) return;

        later(() -> {
            for (int userId : players) {
                try {
                    this.chargeOne(userId, game);
                } catch (SQLException e) {
                    LOGGER.error("[gamecenter] could not charge a game", e);
                }
            }
        });
    }

    private void chargeOne(int userId, String game) throws SQLException {
        int free = this.freePerDay(userId, game);

        if (free < 0) return;

        try (Connection connection = connection()) {
            int[] usage = this.usage(connection, userId, game);

            if (usage[0] < free) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO game_plays (user_id, game, free_day, free_used, extra) VALUES (?, ?, CURDATE(), ?, 0) "
                                + "ON DUPLICATE KEY UPDATE free_day = CURDATE(), free_used = VALUES(free_used)")) {
                    statement.setInt(1, userId);
                    statement.setString(2, game);
                    statement.setInt(3, usage[0] + 1);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE game_plays SET extra = GREATEST(0, extra - 1) WHERE user_id = ? AND game = ?")) {
                    statement.setInt(1, userId);
                    statement.setString(2, game);
                    statement.executeUpdate();
                }
            }
        }
    }

    // ---- Buying games

    public List<Offer> offers() {
        String sql = "SELECT id, games, price_credits, price_points, points_type FROM snowwar_token_offers "
                + "WHERE enabled = '1' ORDER BY order_num, id";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            List<Offer> offers = new ArrayList<>();

            while (result.next()) {
                offers.add(new Offer(
                        result.getInt("id"),
                        result.getInt("games"),
                        result.getInt("price_credits"),
                        result.getInt("price_points"),
                        result.getInt("points_type")));
            }

            return offers;
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read the offers", e);
            return List.of();
        }
    }

    /** Buys a pack of games for one game; answers the error code, or null. */
    public String buy(int userId, String game, int offerId) {
        if (!known(game)) return "no_game";

        User user = onlineUser(userId);

        if (user == null) return "offline";

        Offer offer = this.offers().stream()
                .filter(o -> o.id() == offerId)
                .findFirst()
                .orElse(null);

        if (offer == null || offer.games() <= 0) return "no_offer";

        int credits = user.hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : offer.credits();
        int points = user.hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : offer.points();

        if (!CatalogPaymentService.tryTake(user, credits, offer.pointsType(), points)) return "not_enough";

        String sql = "INSERT INTO game_plays (user_id, game, free_day, free_used, extra) VALUES (?, ?, NULL, 0, ?) "
                + "ON DUPLICATE KEY UPDATE extra = extra + VALUES(extra)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, game);
            statement.setInt(3, offer.games());
            statement.executeUpdate();
            return null;
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not add the games bought", e);
            CatalogPaymentService.refund(user, credits, offer.pointsType(), points);
            return "server_error";
        }
    }

    // ---- Scores

    static LocalDate weekStart(int offset) {
        return LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(offset);
    }

    /** A game ended: these players (robots aside) played it, these won. */
    public void record(String game, Collection<Integer> players, Collection<Integer> winners) {
        List<Integer> humans = players.stream().filter(id -> id > 0).distinct().toList();

        if (humans.isEmpty() || !known(game)) return;

        LocalDate week = weekStart(0);
        String sql =
                "INSERT INTO game_scores (user_id, game, week_start, score, matches, wins) VALUES (?, ?, ?, ?, 1, ?) "
                        + "ON DUPLICATE KEY UPDATE score = score + VALUES(score), matches = matches + 1, wins = wins + VALUES(wins)";

        later(() -> {
            try (Connection connection = connection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int userId : humans) {
                    boolean won = winners.contains(userId);

                    statement.setInt(1, userId);
                    statement.setString(2, game);
                    statement.setDate(3, Date.valueOf(week));
                    statement.setInt(4, won ? WIN_POINTS : PLAY_POINTS);
                    statement.setInt(5, won ? 1 : 0);
                    statement.addBatch();
                }

                statement.executeBatch();
            } catch (SQLException e) {
                LOGGER.error("[gamecenter] could not write the scores", e);
            }
        });
    }

    /**
     * A leaderboard of a game: {@code weekly} (this week, or {@code weekOffset} weeks ago) or of all time; of everyone,
     * of the player's friends, or of the groups (each counted with its members' scores, by their favourite group).
     */
    public JsonObject leaderboard(int userId, String game, boolean weekly, String scope, int weekOffset) {
        JsonObject view = new JsonObject();
        int maxOffset = this.maxOffset(game);
        int offset = Math.max(0, Math.min(weekOffset, maxOffset));
        LocalDate week = weekStart(offset);
        boolean groups = "groups".equals(scope);
        boolean friends = "friends".equals(scope);
        String scores = weekly
                ? "SELECT user_id, score FROM game_scores WHERE game = ? AND week_start = ?"
                : "SELECT user_id, SUM(score) score FROM game_scores WHERE game = ? GROUP BY user_id";
        String sql = groups
                ? "WITH scores AS (" + scores + "), grouped AS ("
                        + "SELECT users_settings.guild_id id, SUM(scores.score) score FROM scores "
                        + "INNER JOIN users_settings ON users_settings.user_id = scores.user_id "
                        + "WHERE scores.score > 0 AND users_settings.guild_id > 0 GROUP BY users_settings.guild_id) "
                        + "SELECT grouped.id, grouped.score, RANK() OVER (ORDER BY grouped.score DESC) position, "
                        + "guilds.name, guilds.badge figure, 'g' gender FROM grouped "
                        + "INNER JOIN guilds ON guilds.id = grouped.id ORDER BY position, grouped.id LIMIT 50"
                : "WITH scores AS (" + scores + ") SELECT scores.user_id id, scores.score, "
                        + "RANK() OVER (ORDER BY scores.score DESC) position, users.username name, users.look figure, "
                        + "users.gender FROM scores INNER JOIN users ON users.id = scores.user_id WHERE scores.score > 0"
                        + (friends
                                ? " AND (scores.user_id = ? OR EXISTS (SELECT 1 FROM messenger_friendships "
                                        + "WHERE user_one_id = ? AND user_two_id = scores.user_id))"
                                : "")
                        + " ORDER BY position, scores.user_id LIMIT 50";
        JsonArray entries = new JsonArray();

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;

            statement.setString(index++, game);
            if (weekly) statement.setDate(index++, Date.valueOf(week));
            if (friends && !groups) {
                statement.setInt(index++, userId);
                statement.setInt(index, userId);
            }

            try (ResultSet result = statement.executeQuery()) {
                int rank = 0;

                while (result.next()) {
                    JsonObject entry = new JsonObject();

                    // Among friends, the ranks are counted among them.
                    rank = friends ? rank + 1 : result.getInt("position");
                    entry.addProperty("id", result.getInt("id"));
                    entry.addProperty("rank", rank);
                    entry.addProperty("name", result.getString("name"));
                    entry.addProperty("figure", result.getString("figure"));
                    entry.addProperty("gender", result.getString("gender"));
                    entry.addProperty("score", result.getInt("score"));
                    entries.add(entry);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read the leaderboard", e);
        }

        LocalDateTime next = weekStart(0).plusWeeks(1).atStartOfDay();

        view.addProperty("game", game);
        view.addProperty("weekly", weekly);
        view.addProperty("scope", groups ? "groups" : friends ? "friends" : "everyone");
        view.addProperty("weekOffset", offset);
        view.addProperty("maxOffset", maxOffset);
        view.addProperty("year", week.get(WeekFields.ISO.weekBasedYear()));
        view.addProperty("week", week.get(WeekFields.ISO.weekOfWeekBasedYear()));
        view.addProperty("minutesUntilReset", Math.max(0, ChronoUnit.MINUTES.between(LocalDateTime.now(), next)));
        view.addProperty("favouriteGroupId", groups ? this.favouriteGroup(userId) : 0);
        view.add("entries", entries);
        return view;
    }

    private int maxOffset(String game) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT MIN(week_start) FROM game_scores WHERE game = ?")) {
            statement.setString(1, game);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getDate(1) == null) return 0;

                return Math.max(
                        0, (int) ChronoUnit.WEEKS.between(result.getDate(1).toLocalDate(), weekStart(0)));
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private int favouriteGroup(int userId) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT guild_id FROM users_settings WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ---- The list

    /** What the Game Center shows: each game's players now and the games left, the packs of games. */
    public JsonObject overview(int userId, Map<String, Integer> playersNow) {
        JsonObject view = new JsonObject();
        JsonArray games = new JsonArray();
        User user = onlineUser(userId);

        for (String game : GAMES) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", game);
            entry.addProperty("playing", playersNow.getOrDefault(game, 0));
            entry.addProperty("gamesLeft", this.gamesLeft(userId, game));
            entry.addProperty("freePerDay", this.freePerDay(userId, game));
            games.add(entry);
        }

        JsonArray offers = new JsonArray();

        for (Offer offer : this.offers()) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", offer.id());
            entry.addProperty("games", offer.games());
            entry.addProperty("credits", offer.credits());
            entry.addProperty("points", offer.points());
            entry.addProperty("pointsType", offer.pointsType());
            offers.add(entry);
        }

        view.addProperty("club", user != null && user.getUserStats().hasActiveClub());
        view.add("games", games);
        view.add("offers", offers);
        return view;
    }
}
