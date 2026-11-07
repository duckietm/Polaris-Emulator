package polaris.hotel.games.lostmonkey;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.games.center.GameCenter;

/**
 * Lost Monkey's islands (`/api/lostmonkey`): each player's island, kept in `lostmonkey_islands` and in memory while it
 * is played, the actions on it (pick bananas, feed, wash, the TV, sleep, buy bananas, build and launch the rocket), and
 * the island's games (Jungle Jam, Banana Drama): a game is one of the day's games of Lost Monkey at the Game Center,
 * started here and scored here (its coins and its points in the leaderboard).
 */
public final class LostMonkeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LostMonkeyManager.class);
    private static final LostMonkeyManager INSTANCE = new LostMonkeyManager();
    /** The Game Center's game of Lost Monkey. */
    static final String GAME = "copywriter";
    /** The island's games, their shortest game, the score that counts as a win. */
    private static final List<String> GAMES = List.of("junglejam", "bananadrama");

    private static final long SHORTEST_MS = 15_000L;
    private static final long LONGEST_MS = 10 * 60_000L;
    private static final int WIN_SCORE = 1500;
    /** A score cannot grow faster than this, a second. */
    private static final int MOST_A_SECOND = 120;

    private final Map<Integer, LostMonkeyIsland> islands = new ConcurrentHashMap<>();
    private final Map<Integer, long[]> plays = new ConcurrentHashMap<>();

    private LostMonkeyManager() {}

    public static LostMonkeyManager get() {
        return INSTANCE;
    }

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("lostmonkey.enabled", true);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** The players on their island now, for the Game Center's list. */
    public int playersNow() {
        return this.plays.size();
    }

    private LostMonkeyIsland island(int userId, long now) {
        LostMonkeyIsland island = this.islands.computeIfAbsent(userId, id -> this.load(id, now));

        island.advance(now);
        return island;
    }

    private LostMonkeyIsland load(int userId, long now) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT state FROM lostmonkey_islands WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next())
                    return LostMonkeyIsland.fromJson(
                            JsonParser.parseString(result.getString(1)).getAsJsonObject(), now);
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.error("[lostmonkey] could not read an island", e);
        }

        return LostMonkeyIsland.fresh(now, userId);
    }

    private void save(int userId, LostMonkeyIsland island) {
        String state = island.toJson().toString();
        String sql = "INSERT INTO lostmonkey_islands (user_id, state) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE state = VALUES(state)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, state);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[lostmonkey] could not save an island", e);
        }
    }

    public JsonObject view(int userId) {
        if (!enabled()) throw new LostMonkeyException("disabled");

        long now = System.currentTimeMillis();
        LostMonkeyIsland island = this.island(userId, now);

        synchronized (island) {
            JsonObject view = island.view(now);

            view.addProperty("gamesLeft", GameCenter.get().gamesLeft(userId, GAME));
            return view;
        }
    }

    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new LostMonkeyException("disabled");

        long now = System.currentTimeMillis();
        LostMonkeyIsland island = this.island(userId, now);
        JsonObject extra = new JsonObject();

        synchronized (island) {
            if (!island.created && !action.equals("create")) throw new LostMonkeyException("not_created");

            switch (action) {
                case "create" -> island.create(intOf(body, "colour"), stringOf(body, "name"));
                case "colour" -> island.setColour(intOf(body, "colour"));
                case "harvest" -> island.harvest(intOf(body, "tree"));
                case "feed" -> island.feed();
                case "wash" -> island.wash();
                case "tv" -> island.watchTv(now);
                case "sleep" -> island.sleep();
                case "wake" -> island.wake();
                case "bananas" -> island.buyBananas(intOf(body, "count"));
                case "build" -> island.buildRocket();
                case "launch" -> island.launch();
                case "play" -> this.play(userId, island, body, now);
                case "score" -> extra.addProperty("coinsWon", this.score(userId, island, body, now));
                default -> throw new LostMonkeyException("unknown_action");
            }

            this.save(userId, island);
        }

        JsonObject view = this.view(userId);

        for (String key : extra.keySet()) view.add(key, extra.get(key));

        return view;
    }

    private void play(int userId, LostMonkeyIsland island, JsonObject body, long now) {
        int game = GAMES.indexOf(
                body != null && body.has("game") ? body.get("game").getAsString() : "");

        if (game < 0) throw new LostMonkeyException("no_game");
        if (GameCenter.get().gamesLeft(userId, GAME) == 0) throw new LostMonkeyException("no_games_left");

        island.play();
        GameCenter.get().charge(List.of(userId), GAME);
        this.plays.put(userId, new long[] {now, game});
    }

    private int score(int userId, LostMonkeyIsland island, JsonObject body, long now) {
        long[] play = this.plays.remove(userId);

        if (play == null) throw new LostMonkeyException("no_game");

        long played = now - play[0];
        int score = Math.max(0, intOf(body, "score"));

        if (played < SHORTEST_MS || played > LONGEST_MS) return 0;

        score = (int) Math.min(score, played / 1000 * MOST_A_SECOND);
        GameCenter.get().record(GAME, List.of(userId), score >= WIN_SCORE ? List.of(userId) : List.of());

        return island.reward(score, LocalDate.now().toEpochDay());
    }

    private static String stringOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static int intOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsInt() : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
