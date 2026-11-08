package polaris.hotel.games.fastfood;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.Scheduler;
import polaris.hotel.games.center.GameCenter;
import polaris.hotel.users.User;

/**
 * Fast Food: the games (kept in memory), the clock that runs them when nobody asks, the scores written to
 * `fastfood_stats`, and what each player sees (`/api/fastfood`).
 */
public final class FastFoodManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastFoodManager.class);
    private static final FastFoodManager INSTANCE = new FastFoodManager();
    /** A player whose window has not asked for the game for this long has left it. */
    private static final long GONE_MS = 20_000L;

    private final Map<Integer, FastFoodMatch> matches = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> matchOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextMatchId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private FastFoodManager() {}

    public static FastFoodManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("fastfood.enabled", true);
    }

    private static int setting(String key, int fallback) {
        return Emulator.getConfig().getInt(key, fallback);
    }

    private static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** The players at a table now, for the Game Center's list. */
    public int playersNow() {
        return this.matchOfUser.size();
    }

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new FastFoodException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "gameId", 0), now);
            case "leave" -> this.leave(userId);
            default -> this.onMatch(userId, action, body, now);
        }

        return this.view(userId);
    }

    private void create(int userId, JsonObject body, long now) {
        this.leave(userId);

        if (this.matches.size() >= setting("fastfood.games.max", 10)) throw new FastFoodException("too_many_games");

        if (GameCenter.get().gamesLeft(userId, "fastfood") == 0) throw new FastFoodException("no_games_left");

        User user = this.player(userId);
        FastFoodMatch match = new FastFoodMatch(
                this.nextMatchId.getAndIncrement(),
                FastFoodRules.from(rulesOf(body), FastFoodRules.standard()),
                this.random);

        match.join(userId, user.getUserInfo().getUsername(), user.getUserInfo().getLook(), gender(user), now);
        this.matches.put(match.id(), match);
        this.matchOfUser.put(userId, match.id());
    }

    private void join(int userId, int matchId, long now) {
        FastFoodMatch match = this.matches.get(matchId);

        if (match == null) throw new FastFoodException("no_table");
        if (Integer.valueOf(matchId).equals(this.matchOfUser.get(userId))) return;

        if (GameCenter.get().gamesLeft(userId, "fastfood") == 0) throw new FastFoodException("no_games_left");

        User user = this.player(userId);

        this.leave(userId);

        synchronized (match) {
            match.join(
                    userId, user.getUserInfo().getUsername(), user.getUserInfo().getLook(), gender(user), now);
        }

        this.matchOfUser.put(userId, matchId);
    }

    public void leave(int userId) {
        Integer matchId = this.matchOfUser.remove(userId);
        FastFoodMatch match = matchId == null ? null : this.matches.get(matchId);

        if (match == null) return;

        synchronized (match) {
            match.leave(userId, System.currentTimeMillis());
            this.recordIfFinished(match);
            if (!match.hasPlayers()) this.matches.remove(match.id());
        }
    }

    private void onMatch(int userId, String action, JsonObject body, long now) {
        FastFoodMatch match = this.matchOf(userId);

        if (match == null) throw new FastFoodException("no_table");

        synchronized (match) {
            match.advance(now);

            switch (action) {
                case "rules" -> match.setRules(userId, FastFoodRules.from(rulesOf(body), match.rules()));
                case "bot" -> match.addBot(userId, now);
                case "bot/remove" -> match.removeBot(userId);
                case "start" -> {
                    List<Integer> humans = humansOf(match.players());
                    String key = "fastfood";

                    if (GameCenter.get().firstWithoutGames(humans, key) != 0)
                        throw new FastFoodException("no_games_left");

                    match.start(userId, now);
                    GameCenter.get().charge(humans, key);
                }
                case "drop" -> match.drop(userId, now);
                case "chute" -> match.chute(userId, longOf(body, "elapsed", -1), now);
                case "power" -> match.usePower(userId, now);
                default -> throw new FastFoodException("unknown_action");
            }

            this.recordIfFinished(match);
        }
    }

    /** The players of a game, robots aside (the Game Center counts their games and scores). */
    private static List<Integer> humansOf(List<FastFoodMatch.Player> seats) {
        List<Integer> humans = new ArrayList<>();

        for (FastFoodMatch.Player seat : seats) {
            if (!seat.bot()) humans.add(seat.userId());
        }

        return humans;
    }

    // ---- The clock

    private void startClock() {
        if (!this.clockStarted.compareAndSet(false, true)) return;

        new Scheduler(1) {
            @Override
            protected void tick() {
                FastFoodManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (FastFoodMatch match : new ArrayList<>(this.matches.values())) {
            try {
                synchronized (match) {
                    for (FastFoodMatch.Player player : new ArrayList<>(match.players())) {
                        if (!player.bot() && now - player.lastSeen() > GONE_MS) {
                            this.matchOfUser.remove(player.userId(), match.id());
                            match.leave(player.userId(), now);
                        }
                    }

                    match.advance(now);
                    this.recordIfFinished(match);

                    if (!match.hasPlayers()) this.matches.remove(match.id());
                }
            } catch (FastFoodException e) {
                LOGGER.debug("[fastfood] match {}: {}", match.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[fastfood] match {} failed, closing it", match.id(), e);
                this.matches.remove(match.id());
                this.matchOfUser.values().removeIf(id -> id == match.id());
            }
        }
    }

    // ---- Scores

    private void recordIfFinished(FastFoodMatch match) {
        if (!match.takeRecord()) return;

        List<int[]> players = new ArrayList<>();

        for (FastFoodMatch.Player player : match.players()) {
            if (player.bot()) continue;

            players.add(new int[] {player.userId(), match.winners().contains(player.userId()) ? 1 : 0, player.stars()});
        }

        GameCenter.get()
                .record(
                        "fastfood",
                        players.stream().map(player -> player[0]).toList(),
                        players.stream()
                                .filter(player -> player[1] == 1)
                                .map(player -> player[0])
                                .toList());

        if (players.isEmpty()) return;

        Emulator.getThreading().run(() -> this.writeStats(players));
    }

    private void writeStats(List<int[]> players) {
        String sql = "INSERT INTO fastfood_stats (user_id, games, wins, stars) VALUES (?, 1, ?, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins), stars = stars + VALUES(stars)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int[] player : players) {
                statement.setInt(1, player[0]);
                statement.setInt(2, player[1]);
                statement.setInt(3, player[2]);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[fastfood] could not write the scores", e);
        }
    }

    // ---- What a player sees

    public JsonObject view(int userId) {
        JsonObject view = new JsonObject();
        FastFoodMatch match = this.matchOf(userId);
        long now = System.currentTimeMillis();

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);
        view.add("physics", FastFoodViews.physics());

        if (match != null) {
            synchronized (match) {
                match.seen(userId, now);
                match.advance(now);
                this.recordIfFinished(match);
                view.add("game", FastFoodViews.match(match, userId, now));
            }
        }

        JsonArray list = new JsonArray();

        for (FastFoodMatch other : this.matches.values()) {
            synchronized (other) {
                list.add(FastFoodViews.summary(other));
            }
        }

        view.add("games", list);
        return view;
    }

    // ---- Helpers

    private FastFoodMatch matchOf(int userId) {
        Integer matchId = this.matchOfUser.get(userId);

        return matchId == null ? null : this.matches.get(matchId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new FastFoodException("offline");

        return user;
    }

    private static String gender(User user) {
        return user.getUserInfo().getGender() == null
                ? "M"
                : user.getUserInfo().getGender().name();
    }

    private static int intOf(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long longOf(JsonObject body, String key, long fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsLong() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonObject rulesOf(JsonObject body) {
        return body != null && body.has("rules") && body.get("rules").isJsonObject()
                ? body.getAsJsonObject("rules")
                : null;
    }
}
