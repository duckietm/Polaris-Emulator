package polaris.hotel.games.werewolf;

import polaris.Emulator;
import polaris.core.Scheduler;
import polaris.hotel.users.User;
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

/**
 * Habbi's werewolf game: the villages (kept in memory), who sits where, the clock that plays the robots and ends the
 * steps, the winners written to `werewolf_stats`, and what each player sees (`/api/werewolf`).
 */
public final class WerewolfManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerewolfManager.class);
    private static final WerewolfManager INSTANCE = new WerewolfManager();
    /** A player whose window has not asked for the village for this long has left it. */
    private static final long GONE_MS = 45_000L;

    private static final int MAX_MESSAGE = 120;

    private final Map<Integer, WerewolfGame> games = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> gameOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextGameId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private WerewolfManager() {}

    public static WerewolfManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("werewolf.enabled", true);
    }

    private static int setting(String key, int fallback) {
        return Emulator.getConfig().getInt(key, fallback);
    }

    private static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }

    private static String filtered(String text, User user) {
        return Emulator.getGameEnvironment().getWordFilter().filter(text, user);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new WerewolfException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "gameId", 0), intOf(body, "chair", -1), now);
            case "leave" -> this.leave(userId);
            default -> this.onGame(userId, action, body, now);
        }

        return this.view(userId);
    }

    private void create(int userId, JsonObject body, long now) {
        this.leave(userId);

        if (this.games.size() >= setting("werewolf.games.max", 10)) throw new WerewolfException("too_many_games");

        User user = this.player(userId);
        WerewolfGame game = new WerewolfGame(
                this.nextGameId.getAndIncrement(),
                WerewolfRules.from(rulesOf(body), WerewolfRules.standard()),
                this.random);

        game.join(
                userId,
                user.getUserInfo().getUsername(),
                user.getUserInfo().getLook(),
                gender(user),
                intOf(body, "chair", -1),
                now);
        this.games.put(game.id(), game);
        this.gameOfUser.put(userId, game.id());
    }

    private void join(int userId, int gameId, int chair, long now) {
        WerewolfGame game = this.games.get(gameId);

        if (game == null) throw new WerewolfException("no_table");
        if (Integer.valueOf(gameId).equals(this.gameOfUser.get(userId))) return;

        User user = this.player(userId);

        this.leave(userId);

        synchronized (game) {
            game.join(
                    userId,
                    user.getUserInfo().getUsername(),
                    user.getUserInfo().getLook(),
                    gender(user),
                    chair,
                    now);
        }

        this.gameOfUser.put(userId, gameId);
    }

    public void leave(int userId) {
        Integer gameId = this.gameOfUser.remove(userId);
        WerewolfGame game = gameId == null ? null : this.games.get(gameId);

        if (game == null) return;

        synchronized (game) {
            boolean playing = game.state() == WerewolfGame.State.PLAYING;

            game.leave(userId, System.currentTimeMillis());
            if (playing) this.recordIfFinished(game);
            if (!game.hasPlayers()) this.games.remove(game.id());
        }
    }

    private void onGame(int userId, String action, JsonObject body, long now) {
        WerewolfGame game = this.gameOf(userId);

        if (game == null) throw new WerewolfException("no_table");

        synchronized (game) {
            boolean playing = game.state() == WerewolfGame.State.PLAYING;

            switch (action) {
                case "sit" -> game.sit(userId, intOf(body, "chair", -1));
                case "rules" -> game.setRules(userId, WerewolfRules.from(rulesOf(body), game.rules()));
                case "bot" -> game.addBot(userId, now);
                case "bots" -> game.fillWithBots(userId, now);
                case "bot/remove" -> game.removeBot(userId, intOf(body, "userId", 0));
                case "start" -> game.start(userId, now);
                case "act" -> game.act(userId, intOf(body, "target", 0), intOf(body, "second", 0), now);
                case "witch" -> game.witch(userId, boolOf(body, "save"), intOf(body, "poison", 0), now);
                case "ready" -> game.ready(userId, now);
                case "say" -> {
                    String text = stringOf(body, "text").replaceAll("\\s+", " ").trim();

                    if (text.isEmpty()) return;
                    if (text.length() > MAX_MESSAGE) text = text.substring(0, MAX_MESSAGE);

                    game.say(userId, filtered(text, this.player(userId)));
                }
                default -> throw new WerewolfException("unknown_action");
            }

            if (playing) this.recordIfFinished(game);
        }
    }

    // ---- The clock

    private void startClock() {
        if (!this.clockStarted.compareAndSet(false, true)) return;

        new Scheduler(1) {
            @Override
            protected void tick() {
                WerewolfManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (WerewolfGame game : new ArrayList<>(this.games.values())) {
            try {
                synchronized (game) {
                    boolean playing = game.state() == WerewolfGame.State.PLAYING;

                    for (WerewolfGame.Seat seat : new ArrayList<>(game.seats())) {
                        if (!seat.bot() && now - seat.lastSeen > GONE_MS) {
                            this.gameOfUser.remove(seat.userId(), game.id());
                            game.leave(seat.userId(), now);
                        }
                    }

                    game.tick(now);

                    if (playing) this.recordIfFinished(game);
                    if (!game.hasPlayers()) this.games.remove(game.id());
                }
            } catch (WerewolfException e) {
                LOGGER.debug("[werewolf] game {}: {}", game.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[werewolf] game {} failed, closing it", game.id(), e);
                this.games.remove(game.id());
                this.gameOfUser.values().removeIf(id -> id == game.id());
            }
        }
    }

    // ---- Winners

    private void recordIfFinished(WerewolfGame game) {
        if (game.state() != WerewolfGame.State.FINISHED) return;

        List<int[]> players = new ArrayList<>();

        for (WerewolfGame.Seat seat : game.seats()) {
            if (!seat.bot()) players.add(new int[] {seat.userId(), WerewolfViews.won(game, seat) ? 1 : 0});
        }

        if (players.isEmpty()) return;

        Emulator.getThreading().run(() -> this.writeStats(players));
    }

    private void writeStats(List<int[]> players) {
        String sql = "INSERT INTO werewolf_stats (user_id, games, wins) VALUES (?, 1, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int[] player : players) {
                statement.setInt(1, player[0]);
                statement.setInt(2, player[1]);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[werewolf] could not write the scores", e);
        }
    }

    // ---- What a player sees

    public JsonObject view(int userId) {
        JsonObject view = new JsonObject();
        WerewolfGame game = this.gameOf(userId);
        long now = System.currentTimeMillis();

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);

        if (game != null) {
            synchronized (game) {
                game.seen(userId, now);
                view.add("game", WerewolfViews.game(game, userId, now));
            }
        }

        JsonArray list = new JsonArray();

        for (WerewolfGame other : this.games.values()) {
            synchronized (other) {
                list.add(WerewolfViews.summary(other));
            }
        }

        view.add("games", list);
        return view;
    }

    // ---- Helpers

    private WerewolfGame gameOf(int userId) {
        Integer gameId = this.gameOfUser.get(userId);

        return gameId == null ? null : this.games.get(gameId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new WerewolfException("offline");

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

    private static boolean boolOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) && body.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String stringOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static JsonObject rulesOf(JsonObject body) {
        return body != null && body.has("rules") && body.get("rules").isJsonObject()
                ? body.getAsJsonObject("rules")
                : null;
    }
}
