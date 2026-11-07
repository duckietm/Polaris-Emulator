package polaris.hotel.games.center;

import polaris.Emulator;
import polaris.core.Scheduler;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Habbi's Game Center for the games other than SnowStorm (which keeps its own): the free games of the day (fewer
 * without the Habbo Club), the games bought on top, game by game, and the scores of the week and of all time.
 *
 * <p>A game is charged to each player when it starts (robots aside); a player with no game left cannot start or join
 * one. Each game's settings come from the Moderation (`gamecenter_settings`: free games a day, -1 unlimited, and the
 * free games a club member gets on top; `gamecenter_offers`: the packs, their price and currency, -1 being credits).
 *
 * <p>Scores: a win is worth {@link #WIN_POINTS}, a game played {@link #PLAY_POINTS}; added up by ISO week in
 * {@code gamecenter_scores}. Rewards (`gamecenter_rewards`) are given at the end of a game (to every player, or to the
 * winners) and on Monday for last week's leaderboard ({@link #payWeeks}); SnowStorm's too (`snowwar`), from its own
 * scores.
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

    /** A pack of games of a game: how many games, the price, the currency (-1: credits, else the points' type). */
    public record Offer(int id, int games, int price, int currency) {}

    /** A game's settings: free games a day (-1 unlimited), the free games a club member gets on top (-1 unlimited). */
    record Settings(int freeDaily, int hcExtra) {}

    private static final long CACHE_MS = 30_000L;
    private final Map<String, Settings> settings = new ConcurrentHashMap<>();
    private final AtomicLong settingsAt = new AtomicLong();
    private final AtomicBoolean clockStarted = new AtomicBoolean();

    /** A line of a leaderboard: a player, or a group (gender "g", figure = its badge). */
    public record Entry(int id, int rank, String name, String figure, String gender, int score) {}

    private GameCenter() {}

    public static GameCenter get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    /** Every hour: last week's leaderboards pay their rewards (once a week a game). */
    private void startClock() {
        if (!this.clockStarted.compareAndSet(false, true)) return;

        new Scheduler(3600) {
            @Override
            protected void tick() {
                GameCenter.this.payWeeks();
            }
        }.restart();
        later(this::payWeeks);
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

    static int setting(String key, int fallback) {
        return Emulator.getConfig().getInt(key, fallback);
    }

    static void updateSetting(String key, String value) {
        Emulator.getConfig().update(key, value);
    }

    static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }

    static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static void later(Runnable task) {
        Emulator.getThreading().run(task);
    }

    // ---- Games left

    /** The settings of every game, read again every 30 s (and at once after the Moderation saves them). */
    Settings settings(String game) {
        long now = System.currentTimeMillis();

        if (now - this.settingsAt.get() > CACHE_MS) {
            this.settingsAt.set(now);

            try (Connection connection = connection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT game, free_daily, hc_extra FROM gamecenter_settings");
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    this.settings.put(
                            result.getString("game"),
                            new Settings(result.getInt("free_daily"), result.getInt("hc_extra")));
                }
            } catch (SQLException e) {
                LOGGER.error("[gamecenter] could not read the settings", e);
            }
        }

        return this.settings.getOrDefault(game, new Settings(10, 10));
    }

    void forgetSettings() {
        this.settingsAt.set(0);
    }

    /** Free games a day for this player in this game; -1 unlimited. */
    public int freePerDay(int userId, String game) {
        Settings settings = this.settings(game);
        User user = onlineUser(userId);
        boolean member = user != null && user.getUserStats().hasActiveClub();

        if (settings.freeDaily() < 0) return -1;
        if (!member) return settings.freeDaily();

        return settings.hcExtra() < 0 ? -1 : settings.freeDaily() + Math.max(0, settings.hcExtra());
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

    /** The packs of games of a game. */
    public List<Offer> offers(String game) {
        String sql = "SELECT id, games, price, currency FROM gamecenter_offers WHERE game = ? AND games > 0 "
                + "ORDER BY order_num, id";
        List<Offer> offers = new ArrayList<>();

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, game);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    offers.add(new Offer(
                            result.getInt("id"),
                            result.getInt("games"),
                            result.getInt("price"),
                            result.getInt("currency")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read the offers", e);
        }

        return offers;
    }

    /** Buys a pack of games for one game; answers the error code, or null. */
    public String buy(int userId, String game, int offerId) {
        if (!known(game)) return "no_game";

        User user = onlineUser(userId);

        if (user == null) return "offline";

        Offer offer = this.offers(game).stream()
                .filter(o -> o.id() == offerId)
                .findFirst()
                .orElse(null);

        if (offer == null || offer.games() <= 0) return "no_offer";

        boolean credits = offer.currency() < 0;
        int creditPrice = credits && !user.hasPermission(Permission.ACC_INFINITE_CREDITS) ? offer.price() : 0;
        int pointPrice = !credits && !user.hasPermission(Permission.ACC_INFINITE_POINTS) ? offer.price() : 0;
        int pointsType = credits ? 0 : offer.currency();

        if (!CatalogPaymentService.tryTake(user, creditPrice, pointsType, pointPrice)) return "not_enough";

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
            CatalogPaymentService.refund(user, creditPrice, pointsType, pointPrice);
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

            this.rewardMatch(game, humans, winners);
        });
    }

    // ---- Rewards

    List<GameRewards.Reward> rewards(String game) {
        List<GameRewards.Reward> rewards = new ArrayList<>();
        String sql = "SELECT kind, rank_from, rank_to, reward_type, reward_value, amount FROM gamecenter_rewards "
                + "WHERE game = ? ORDER BY kind, rank_from, id";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, game);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rewards.add(new GameRewards.Reward(
                            game,
                            result.getString("kind"),
                            result.getInt("rank_from"),
                            result.getInt("rank_to"),
                            result.getString("reward_type"),
                            result.getString("reward_value"),
                            result.getInt("amount")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read the rewards", e);
        }

        return rewards;
    }

    /** The end of a game: the rewards for playing, and for winning. Called off the game's thread. */
    public void rewardMatch(String game, Collection<Integer> players, Collection<Integer> winners) {
        List<GameRewards.Reward> rewards = this.rewards(game);

        if (rewards.isEmpty()) return;

        for (int userId : players) {
            if (userId <= 0) continue;

            List<String> given = new ArrayList<>();

            for (GameRewards.Reward reward : rewards) {
                boolean due = reward.kind().equals("play") || (reward.kind().equals("win") && winners.contains(userId));

                if (!due) continue;

                String what = GameRewards.give(userId, reward, "gamecenter." + game + "." + reward.kind());

                if (what != null) given.add(what);
            }

            if (!given.isEmpty()) GameRewards.tell(userId, "Bravo ! Tu gagnes " + String.join(", ", given) + ".");
        }
    }

    /** Last week's leaderboard of each game pays its rewards, once. */
    void payWeeks() {
        LocalDate week = weekStart(1);
        List<String> games = new ArrayList<>(GAMES);

        games.add("snowwar");

        for (String game : games) {
            List<GameRewards.Reward> rewards = this.rewards(game).stream()
                    .filter(reward -> reward.kind().equals("week"))
                    .toList();

            if (rewards.isEmpty() || !this.claimWeek(game, week)) continue;

            int deepest =
                    rewards.stream().mapToInt(GameRewards.Reward::rankTo).max().orElse(0);
            List<Integer> ranking = this.ranking(game, week, deepest);

            for (int index = 0; index < ranking.size(); index++) {
                int rank = index + 1;
                int userId = ranking.get(index);
                List<String> given = new ArrayList<>();

                for (GameRewards.Reward reward : rewards) {
                    if (rank < reward.rankFrom() || rank > reward.rankTo()) continue;

                    String what = GameRewards.give(userId, reward, "gamecenter." + game + ".week");

                    if (what != null) given.add(what);
                }

                if (!given.isEmpty())
                    GameRewards.tell(
                            userId,
                            "Classement de la semaine : tu finis " + rank + (rank == 1 ? "er" : "e") + " et tu gagnes "
                                    + String.join(", ", given) + ".");
            }
        }
    }

    /** Marks a week as paid; false when it already was. */
    private boolean claimWeek(String game, LocalDate week) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO gamecenter_reward_weeks (game, week_start) VALUES (?, ?)")) {
            statement.setString(1, game);
            statement.setDate(2, Date.valueOf(week));
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not mark a week as paid", e);
            return false;
        }
    }

    /** The best players of a week, best first. */
    private List<Integer> ranking(String game, LocalDate week, int limit) {
        String sql = game.equals("snowwar")
                ? "SELECT user_id FROM snowwar_scores WHERE week_start = ? AND score > 0 ORDER BY score DESC, user_id LIMIT ?"
                : "SELECT user_id FROM gamecenter_scores WHERE game = ? AND week_start = ? AND score > 0 "
                        + "ORDER BY score DESC, wins DESC, user_id LIMIT ?";
        List<Integer> ranking = new ArrayList<>();

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;

            if (!game.equals("snowwar")) statement.setString(index++, game);

            statement.setDate(index++, Date.valueOf(week));
            statement.setInt(index, Math.max(1, Math.min(1000, limit)));

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) ranking.add(result.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("[gamecenter] could not read a week's ranking", e);
        }

        return ranking;
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
                ? "SELECT user_id, score FROM gamecenter_scores WHERE game = ? AND week_start = ?"
                : "SELECT user_id, SUM(score) score FROM gamecenter_scores WHERE game = ? GROUP BY user_id";
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
                        connection.prepareStatement("SELECT MIN(week_start) FROM gamecenter_scores WHERE game = ?")) {
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
            Settings settings = this.settings(game);
            JsonArray offers = new JsonArray();

            for (Offer offer : this.offers(game)) {
                JsonObject item = new JsonObject();

                item.addProperty("id", offer.id());
                item.addProperty("games", offer.games());
                item.addProperty("price", offer.price());
                item.addProperty("currency", offer.currency());
                offers.add(item);
            }

            entry.addProperty("id", game);
            entry.addProperty("playing", playersNow.getOrDefault(game, 0));
            entry.addProperty("gamesLeft", this.gamesLeft(userId, game));
            entry.addProperty("freePerDay", this.freePerDay(userId, game));
            entry.addProperty("freeDaily", settings.freeDaily());
            entry.addProperty("hcExtra", settings.hcExtra());
            entry.add("offers", offers);
            games.add(entry);
        }

        view.addProperty("club", user != null && user.getUserStats().hasActiveClub());
        view.add("games", games);
        return view;
    }
}
