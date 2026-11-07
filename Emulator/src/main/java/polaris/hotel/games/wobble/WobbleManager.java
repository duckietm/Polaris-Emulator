package polaris.hotel.games.wobble;

import polaris.Emulator;
import polaris.core.Scheduler;
import polaris.hotel.games.center.GameCenter;
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
 * Wobble Squabble: the duels (kept in memory), the clock that runs them when nobody asks, the scores written to
 * `wobble_stats`, and what each player sees (`/api/wobble`).
 */
public final class WobbleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WobbleManager.class);
    private static final WobbleManager INSTANCE = new WobbleManager();
    /** A fighter whose window has not asked for the duel for this long has left it. */
    private static final long GONE_MS = 20_000L;

    private final Map<Integer, WobbleMatch> matches = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> matchOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextMatchId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private WobbleManager() {}

    public static WobbleManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("wobble.enabled", true);
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

    /** The players at a duel now, of the original look or of the Next Gen one, for the Game Center's list. */
    public int playersNow(boolean nextGen) {
        int count = 0;

        for (Integer matchId : this.matchOfUser.values()) {
            WobbleMatch match = this.matches.get(matchId);

            if (match != null && match.rules().nextGen() == nextGen) count++;
        }

        return count;
    }

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new WobbleException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "matchId", 0), now);
            case "leave" -> this.leave(userId);
            default -> this.onMatch(userId, action, body, now);
        }

        return this.view(userId);
    }

    private void create(int userId, JsonObject body, long now) {
        this.leave(userId);

        if (this.matches.size() >= setting("wobble.games.max", 10)) throw new WobbleException("too_many_games");

        if (GameCenter.get()
                        .gamesLeft(
                                userId,
                                WobbleRules.from(rulesOf(body), WobbleRules.standard())
                                                .nextGen()
                                        ? "wobblenext"
                                        : "wobble")
                == 0) throw new WobbleException("no_games_left");

        User user = this.player(userId);
        WobbleMatch match = new WobbleMatch(
                this.nextMatchId.getAndIncrement(),
                WobbleRules.from(rulesOf(body), WobbleRules.standard()),
                this.random);

        match.join(
                userId, user.getUserInfo().getUsername(), user.getUserInfo().getLook(), gender(user), now);
        this.matches.put(match.id(), match);
        this.matchOfUser.put(userId, match.id());
    }

    private void join(int userId, int matchId, long now) {
        WobbleMatch match = this.matches.get(matchId);

        if (match == null) throw new WobbleException("no_table");
        if (Integer.valueOf(matchId).equals(this.matchOfUser.get(userId))) return;

        if (GameCenter.get().gamesLeft(userId, match.rules().nextGen() ? "wobblenext" : "wobble") == 0)
            throw new WobbleException("no_games_left");

        User user = this.player(userId);

        this.leave(userId);

        synchronized (match) {
            match.join(
                    userId,
                    user.getUserInfo().getUsername(),
                    user.getUserInfo().getLook(),
                    gender(user),
                    now);
        }

        this.matchOfUser.put(userId, matchId);
    }

    public void leave(int userId) {
        Integer matchId = this.matchOfUser.remove(userId);
        WobbleMatch match = matchId == null ? null : this.matches.get(matchId);

        if (match == null) return;

        synchronized (match) {
            match.leave(userId, System.currentTimeMillis());
            this.recordIfFinished(match);
            if (!match.hasPlayers()) this.matches.remove(match.id());
        }
    }

    private void onMatch(int userId, String action, JsonObject body, long now) {
        WobbleMatch match = this.matchOf(userId);

        if (match == null) throw new WobbleException("no_table");

        synchronized (match) {
            match.advance(now);

            switch (action) {
                case "rules" -> {
                    // The look of a duel (original or Next Gen) stays the one it was opened with.
                    WobbleRules next = WobbleRules.from(rulesOf(body), match.rules());

                    match.setRules(
                            userId,
                            new WobbleRules(
                                    next.roundsToWin(),
                                    next.bots(),
                                    next.botLevel(),
                                    match.rules().nextGen()));
                }
                case "bot" -> match.addBot(userId, now);
                case "bot/remove" -> match.removeBot(userId);
                case "start" -> {
                    List<Integer> humans = humansOf(match.fighters());
                    String key = (match.rules().nextGen() ? "wobblenext" : "wobble");

                    if (GameCenter.get().firstWithoutGames(humans, key) != 0)
                        throw new WobbleException("no_games_left");

                    match.start(userId, now);
                    GameCenter.get().charge(humans, key);
                }
                case "lean" -> match.lean(userId, intOf(body, "dir", 0), now);
                case "push" -> match.push(userId, intOf(body, "dir", 1), now);
                case "move" -> match.move(userId, intOf(body, "dir", 1), now);
                case "stabilise" -> match.stabilise(userId, now);
                default -> throw new WobbleException("unknown_action");
            }

            this.recordIfFinished(match);
        }
    }

    /** The players of a game, robots aside (the Game Center counts their games and scores). */
    private static List<Integer> humansOf(List<WobbleMatch.Fighter> seats) {
        List<Integer> humans = new ArrayList<>();

        for (WobbleMatch.Fighter seat : seats) {
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
                WobbleManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (WobbleMatch match : new ArrayList<>(this.matches.values())) {
            try {
                synchronized (match) {
                    for (WobbleMatch.Fighter fighter : match.fighters()) {
                        if (!fighter.bot() && now - fighter.lastSeen() > GONE_MS) {
                            this.matchOfUser.remove(fighter.userId(), match.id());
                            match.leave(fighter.userId(), now);
                        }
                    }

                    match.advance(now);
                    this.recordIfFinished(match);

                    if (!match.hasPlayers()) this.matches.remove(match.id());
                }
            } catch (WobbleException e) {
                LOGGER.debug("[wobble] match {}: {}", match.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[wobble] match {} failed, closing it", match.id(), e);
                this.matches.remove(match.id());
                this.matchOfUser.values().removeIf(id -> id == match.id());
            }
        }
    }

    // ---- Scores

    private void recordIfFinished(WobbleMatch match) {
        if (!match.takeRecord()) return;

        List<int[]> players = new ArrayList<>();

        for (int side = 0; side < 2; side++) {
            WobbleMatch.Fighter fighter = match.onSide(side);

            if (fighter == null || fighter.bot()) continue;

            WobbleMatch.Fighter other = match.onSide(1 - side);

            players.add(new int[] {
                fighter.userId(), match.winnerSide() == side ? 1 : 0, fighter.wins(), other == null ? 0 : other.wins()
            });
        }

        GameCenter.get()
                .record(
                        match.rules().nextGen() ? "wobblenext" : "wobble",
                        players.stream().map(player -> player[0]).toList(),
                        players.stream()
                                .filter(player -> player[1] == 1)
                                .map(player -> player[0])
                                .toList());

        if (players.isEmpty()) return;

        Emulator.getThreading().run(() -> this.writeStats(players));
    }

    private void writeStats(List<int[]> players) {
        String sql = "INSERT INTO wobble_stats (user_id, games, wins, rounds_won, rounds_lost) VALUES (?, 1, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins), "
                + "rounds_won = rounds_won + VALUES(rounds_won), rounds_lost = rounds_lost + VALUES(rounds_lost)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int[] player : players) {
                statement.setInt(1, player[0]);
                statement.setInt(2, player[1]);
                statement.setInt(3, player[2]);
                statement.setInt(4, player[3]);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[wobble] could not write the scores", e);
        }
    }

    // ---- What a player sees

    public JsonObject view(int userId) {
        JsonObject view = new JsonObject();
        WobbleMatch match = this.matchOf(userId);
        long now = System.currentTimeMillis();

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);

        if (match != null) {
            synchronized (match) {
                match.seen(userId, now);
                match.advance(now);
                this.recordIfFinished(match);
                view.add("match", WobbleViews.match(match, userId, now));
            }
        }

        JsonArray list = new JsonArray();

        for (WobbleMatch other : this.matches.values()) {
            synchronized (other) {
                list.add(WobbleViews.summary(other));
            }
        }

        view.add("matches", list);
        return view;
    }

    // ---- Helpers

    private WobbleMatch matchOf(int userId) {
        Integer matchId = this.matchOfUser.get(userId);

        return matchId == null ? null : this.matches.get(matchId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new WobbleException("offline");

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
