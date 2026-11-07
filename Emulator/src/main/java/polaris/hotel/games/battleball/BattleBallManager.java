package polaris.hotel.games.battleball;

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
 * Habbi's BattleBall: the games (kept in memory), the clock that runs them when nobody asks, the scores written to
 * `battleball_stats`, and what each player sees (`/api/battleball`).
 */
public final class BattleBallManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BattleBallManager.class);
    private static final BattleBallManager INSTANCE = new BattleBallManager();
    /** A player whose window has not asked for the game for this long has left it. */
    private static final long GONE_MS = 20_000L;

    private final Map<Integer, BattleBallMatch> matches = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> matchOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextMatchId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private BattleBallManager() {}

    public static BattleBallManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("battleball.enabled", true);
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

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new BattleBallException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "gameId", 0), intOf(body, "team", -1), now);
            case "leave" -> this.leave(userId);
            default -> this.onMatch(userId, action, body, now);
        }

        return this.view(userId);
    }

    private void create(int userId, JsonObject body, long now) {
        this.leave(userId);

        if (this.matches.size() >= setting("battleball.games.max", 10)) throw new BattleBallException("too_many_games");

        User user = this.player(userId);
        BattleBallMatch match = new BattleBallMatch(
                this.nextMatchId.getAndIncrement(),
                BattleBallRules.from(rulesOf(body), BattleBallRules.standard()),
                this.random);

        match.join(
                userId,
                user.getUserInfo().getUsername(),
                user.getUserInfo().getLook(),
                gender(user),
                intOf(body, "team", -1),
                now);
        this.matches.put(match.id(), match);
        this.matchOfUser.put(userId, match.id());
    }

    private void join(int userId, int matchId, int team, long now) {
        BattleBallMatch match = this.matches.get(matchId);

        if (match == null) throw new BattleBallException("no_table");
        if (Integer.valueOf(matchId).equals(this.matchOfUser.get(userId))) return;

        User user = this.player(userId);

        this.leave(userId);

        synchronized (match) {
            match.join(
                    userId,
                    user.getUserInfo().getUsername(),
                    user.getUserInfo().getLook(),
                    gender(user),
                    team,
                    now);
        }

        this.matchOfUser.put(userId, matchId);
    }

    public void leave(int userId) {
        Integer matchId = this.matchOfUser.remove(userId);
        BattleBallMatch match = matchId == null ? null : this.matches.get(matchId);

        if (match == null) return;

        synchronized (match) {
            match.leave(userId, System.currentTimeMillis());
            this.recordIfFinished(match);
            if (!match.hasPlayers()) this.matches.remove(match.id());
        }
    }

    private void onMatch(int userId, String action, JsonObject body, long now) {
        BattleBallMatch match = this.matchOf(userId);

        if (match == null) throw new BattleBallException("no_table");

        synchronized (match) {
            match.advance(now);

            switch (action) {
                case "rules" -> match.setRules(userId, BattleBallRules.from(rulesOf(body), match.rules()));
                case "team" -> match.switchTeam(userId, intOf(body, "team", 0));
                case "bot" -> match.addBot(userId, intOf(body, "team", -1), now);
                case "bots" -> match.fillWithBots(userId, now);
                case "bot/remove" -> match.removeBot(userId);
                case "start" -> match.start(userId, now);
                case "walk" -> match.walk(userId, intOf(body, "x", -1), intOf(body, "y", -1), now);
                case "power" -> match.usePower(userId, now);
                default -> throw new BattleBallException("unknown_action");
            }

            this.recordIfFinished(match);
        }
    }

    // ---- The clock

    private void startClock() {
        if (!this.clockStarted.compareAndSet(false, true)) return;

        new Scheduler(1) {
            @Override
            protected void tick() {
                BattleBallManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (BattleBallMatch match : new ArrayList<>(this.matches.values())) {
            try {
                synchronized (match) {
                    for (BattleBallMatch.Player player : new ArrayList<>(match.players())) {
                        if (!player.bot() && now - player.lastSeen() > GONE_MS) {
                            this.matchOfUser.remove(player.userId(), match.id());
                            match.leave(player.userId(), now);
                        }
                    }

                    match.advance(now);
                    this.recordIfFinished(match);

                    if (!match.hasPlayers()) this.matches.remove(match.id());
                }
            } catch (BattleBallException e) {
                LOGGER.debug("[battleball] match {}: {}", match.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[battleball] match {} failed, closing it", match.id(), e);
                this.matches.remove(match.id());
                this.matchOfUser.values().removeIf(id -> id == match.id());
            }
        }
    }

    // ---- Scores

    private void recordIfFinished(BattleBallMatch match) {
        if (!match.takeRecord()) return;

        List<int[]> players = new ArrayList<>();

        for (BattleBallMatch.Player player : match.players()) {
            if (player.bot()) continue;

            players.add(new int[] {
                player.userId(), match.winners().contains(player.team()) ? 1 : 0, match.playerScore(player.userId())
            });
        }

        if (players.isEmpty()) return;

        Emulator.getThreading().run(() -> this.writeStats(players));
    }

    private void writeStats(List<int[]> players) {
        String sql = "INSERT INTO battleball_stats (user_id, games, wins, points) VALUES (?, 1, ?, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins), points = points + VALUES(points)";

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
            LOGGER.error("[battleball] could not write the scores", e);
        }
    }

    // ---- What a player sees

    public JsonObject view(int userId) {
        JsonObject view = new JsonObject();
        BattleBallMatch match = this.matchOf(userId);
        long now = System.currentTimeMillis();

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);

        if (match != null) {
            synchronized (match) {
                match.seen(userId, now);
                match.advance(now);
                this.recordIfFinished(match);
                view.add("game", BattleBallViews.match(match, userId, now));
            }
        }

        JsonArray list = new JsonArray();

        for (BattleBallMatch other : this.matches.values()) {
            synchronized (other) {
                list.add(BattleBallViews.summary(other));
            }
        }

        view.add("games", list);
        return view;
    }

    // ---- Helpers

    private BattleBallMatch matchOf(int userId) {
        Integer matchId = this.matchOfUser.get(userId);

        return matchId == null ? null : this.matches.get(matchId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new BattleBallException("offline");

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

    private static JsonObject rulesOf(JsonObject body) {
        return body != null && body.has("rules") && body.get("rules").isJsonObject()
                ? body.getAsJsonObject("rules")
                : null;
    }
}
